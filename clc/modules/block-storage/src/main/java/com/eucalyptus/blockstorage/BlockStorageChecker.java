/*************************************************************************
 * Copyright 2009-2012 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 *
 * This file may incorporate work covered under the following copyright
 * and permission notice:
 *
 *   Software License Agreement (BSD License)
 *
 *   Copyright (c) 2008, Regents of the University of California
 *   All rights reserved.
 *
 *   Redistribution and use of this software in source and binary forms,
 *   with or without modification, are permitted provided that the
 *   following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *   FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *   COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *   INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *   BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *   CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *   LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *   ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *   POSSIBILITY OF SUCH DAMAGE. USERS OF THIS SOFTWARE ACKNOWLEDGE
 *   THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE LICENSED MATERIAL,
 *   COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS SOFTWARE,
 *   AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *   IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA,
 *   SANTA BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY,
 *   WHICH IN THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION,
 *   REPLACEMENT OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO
 *   IDENTIFIED, OR WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT
 *   NEEDED TO COMPLY WITH ANY SUCH LICENSES OR RIGHTS.
 ************************************************************************/

package com.eucalyptus.blockstorage;

import java.util.List;

import com.eucalyptus.entities.Entities;
import com.eucalyptus.entities.TransactionException;
import com.eucalyptus.entities.TransactionResource;
import com.eucalyptus.entities.Transactions;
import org.apache.log4j.Logger;

import com.eucalyptus.blockstorage.entities.SnapshotInfo;
import com.eucalyptus.blockstorage.entities.VolumeInfo;
import com.eucalyptus.blockstorage.util.StorageProperties;
import com.eucalyptus.util.EucalyptusCloudException;

public class BlockStorageChecker {
	private static Logger LOG = Logger.getLogger(BlockStorageChecker.class);
	private LogicalStorageManager blockManager;
	private static boolean transferredPending = false;
	S3SnapshotTransfer snapshotTransfer;

	public BlockStorageChecker(LogicalStorageManager blockManager) {
		this.blockManager = blockManager;
	}

	public void startupChecks() {
		new StartupChecker().start();
	}

	private class StartupChecker extends Thread {
		public StartupChecker() {}

		public void run() {
			try {
				cleanup();
			} catch (EucalyptusCloudException e) {
				LOG.error("Startup cleanup failed", e);
			}
			try {
				blockManager.startupChecks();
			} catch(EucalyptusCloudException e) {
				LOG.error("Startup checks failed");
			}
		}
	}

	public void cleanup() throws EucalyptusCloudException {
        LOG.info("Initiating startup cleanup operation for block storage");
		cleanVolumes();
		cleanSnapshots();
	}

	public void cleanVolumes() {
        LOG.info("Initiating volume cleanup for block storage");
		cleanStuckVolumes();
		cleanFailedVolumes();
	}

	public void cleanSnapshots() {
        LOG.info("Initiating snapshot cleanup for block storage");
		cleanFailedSnapshots();
		cleanStuckSnapshots();
	}

	public void cleanStuckVolumes() {
        VolumeInfo volumeInfo = new VolumeInfo();
        volumeInfo.setStatus(StorageProperties.Status.creating.toString());
        List<VolumeInfo> volumesInCreating;
        try {
            volumesInCreating = Transactions.findAll(volumeInfo);
        } catch(TransactionException f) {
            LOG.warn("Failed to execute query for stuck volumes", f);
            //Nothing to do.
            volumesInCreating = null;
        }

        if(volumesInCreating == null || volumesInCreating.size() == 0) {
            LOG.info("No stuck volumes found. No cleanup needed for stuck volumes");
            return;
        }

        for (VolumeInfo volInfo : volumesInCreating) {
            String volumeId = volInfo.getVolumeId();
            LOG.info("Cleaning stuck volume " + volumeId);
            try {
                blockManager.cleanVolume(volumeId);
                volInfo.setStatus(StorageProperties.Status.failed.toString());
                Transactions.save(volInfo);
            } catch(Throwable f) {
                LOG.warn("Failed updating state of volume " + volumeId + " to failed state after back-end cleanup.");
            }
        }

	}

	public void cleanFailedVolumes() {
		TransactionResource tran = Entities.transactionFor(VolumeInfo.class);
		VolumeInfo volumeInfo = new VolumeInfo();
		volumeInfo.setStatus(StorageProperties.Status.failed.toString());
		List<VolumeInfo> volumeInfos = Entities.query(volumeInfo);
		for(VolumeInfo volInfo : volumeInfos) {
			String volumeId = volInfo.getVolumeId();
			LOG.info("Cleaning failed volume " + volumeId);
			blockManager.cleanVolume(volumeId);
		}
		tran.commit();
	}

	public void cleanFailedVolume(String volumeId) {
        try {
            VolumeInfo volInfo = Transactions.find(new VolumeInfo(volumeId));
			LOG.info("Cleaning failed volume " + volumeId);
			blockManager.cleanVolume(volumeId);
			//We don't remove deleted volumes, but failed are okay to delete.
            Transactions.delete(volInfo);
		} catch(Exception e) {
            LOG.error("Erorr cleaning failed volume");
        }
	}

	public void cleanStuckSnapshots() {
        try(TransactionResource tran = Entities.transactionFor(SnapshotInfo.class)) {
            SnapshotInfo snapshotInfo = new SnapshotInfo();
            snapshotInfo.setStatus(StorageProperties.Status.creating.toString());
            List<SnapshotInfo> snapshotInfos = Entities.query(snapshotInfo, false);
            for (SnapshotInfo snapInfo : snapshotInfos) {
                // Mark them as failed so that it gets reflected in the CLC and the clean up routine picks them up later
                snapInfo.setStatus(StorageProperties.Status.failed.toString());
            }
            tran.commit();
        } catch(Throwable f) {
            LOG.warn("Failed cleaning stuck snapshots", f);
        }
	}

	public void cleanFailedSnapshots() {
        SnapshotInfo snapshotInfo = new SnapshotInfo();
        snapshotInfo.setStatus(StorageProperties.Status.failed.toString());
        List<SnapshotInfo> snapshotInfos;
        try {
            snapshotInfos = Transactions.findAll(snapshotInfo);
        } catch(TransactionException e) {
            LOG.error("Error querying database for failed snapshots", e);
            snapshotInfos = null;
        }
        if(snapshotInfos == null || snapshotInfos.size() == 0) {
            LOG.info("No failed snapshots found to clean");
            return;
        }

        for (SnapshotInfo snapInfo : snapshotInfos) {
            try {
                cleanSnapshot(snapInfo);
                Transactions.delete(snapInfo);
            } catch(Throwable f) {
                LOG.warn("Failed cleaning failed snapshots", f);
            }
        }
    }

	public void cleanFailedSnapshot(String snapshotId) {
        SnapshotInfo snapshotInfo = new SnapshotInfo(snapshotId);
        try {
            SnapshotInfo snapInfo = Transactions.find(snapshotInfo);
            cleanSnapshot(snapInfo);
            Transactions.delete(snapInfo);
        } catch(Throwable f) {
            LOG.warn("Failed cleaning failed snapshot " + snapshotId, f);
        }
	}
	
	private void cleanSnapshot(SnapshotInfo snapInfo) {
		String snapshotId = snapInfo.getSnapshotId();

		LOG.info("Checker task cleaning up snapshot " + snapshotId);
		try {
			LOG.debug("Disconnecting snapshot " + snapshotId + " from the Storage Controller");
			blockManager.finishVolume(snapshotId);
		} catch (Exception e) {
			LOG.debug("Attempt to disconnect snapshot " + snapshotId + " from Storage Controller failed because: " + e.getMessage());
		}

		try {
			LOG.debug("Cleaning snapshot " + snapshotId + " on storage backend");
			blockManager.cleanSnapshot(snapshotId);
		} catch (Exception e) {
			LOG.debug("Attempt to clean snapshot " + snapshotId + " on storage backend failed because: " + e.getMessage());
		}

		try {
			LOG.debug("Cleaning snapshot " + snapshotId + " from objectsotrage");
			if(snapshotTransfer == null) {
				snapshotTransfer = new S3SnapshotTransfer();
			} 
			String[] names = SnapshotInfo.getSnapshotBucketKeyNames(snapInfo.getSnapshotLocation());
			snapshotTransfer.setSnapshotId(snapshotId);
			snapshotTransfer.setBucketName(names[0]);
			snapshotTransfer.setKeyName(names[1]);
			snapshotTransfer.cancelUpload();
		} catch (Exception e) {
			LOG.debug("Attempt to clean uploaded snapshot " + snapshotId + " from objectstorage failed because: " + e.getMessage());
		}
	}

	public void transferPendingSnapshots() throws EucalyptusCloudException {
		if(!transferredPending) {
			SnapshotTransfer transferrer = new SnapshotTransfer(this);
			transferrer.start();
			transferredPending = true;
		}
	}

	private class SnapshotTransfer extends Thread {
		private BlockStorageChecker checker;
		public SnapshotTransfer(final BlockStorageChecker checker) {
			this.checker = checker;
		}

		public void run() {
//			TODO BROKEN, FIX IT!
//			EntityWrapper<SnapshotInfo> db = StorageProperties.getEntityWrapper();
//			SnapshotInfo snapshotInfo = new SnapshotInfo();
//			snapshotInfo.setShouldTransfer(true);
//			List<SnapshotInfo> snapshotInfos = db.query(snapshotInfo);
//			if(snapshotInfos.size() > 0) {
//				SnapshotInfo snapInfo = snapshotInfos.get(0);
//				String snapshotId = snapInfo.getSnapshotId();
//				List<String> returnValues;
//				try {
//					returnValues = blockManager.prepareForTransfer(snapshotId);
//				} catch (EucalyptusCloudException e) {
//					db.rollback();
//					LOG.error(e);
//					return;
//				}
//				if(returnValues.size() > 0) {
//					String snapshotFileName = returnValues.get(0);
//					File snapshotFile = new File(snapshotFileName);
//					Map<String, String> httpParamaters = new HashMap<String, String>();
//					HttpWriter httpWriter;
//					SnapshotProgressCallback callback = new SnapshotProgressCallback(snapshotId, snapshotFile.length(), StorageProperties.TRANSFER_CHUNK_SIZE);
//					httpWriter = new HttpWriter("PUT", snapshotFile, String.valueOf(snapshotFile.length()), callback, "snapset-" + UUID.randomUUID(), snapshotId, "StoreSnapshot", null, httpParamaters);
//					try {
//						httpWriter.run();
//					} catch(Exception ex) {
//						db.rollback();
//						LOG.error(ex, ex);
//						checker.cleanFailedSnapshot(snapshotId);
//					}
//				}
//			}
//			db.commit();	
		}
	}
}
