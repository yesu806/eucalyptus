/*************************************************************************
 * Copyright 2009-2014 Eucalyptus Systems, Inc.
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

package com.eucalyptus.blockstorage.ceph;

import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Logger;

import com.ceph.rbd.RbdImage;
import com.eucalyptus.blockstorage.ceph.entities.CephInfo;

public class CephInputStream extends InputStream {

	private static final Logger LOG = Logger.getLogger(CephInputStream.class);

	private CephConnectionManager conn;
	private RbdImage rbdImage;
	private long position;
	private boolean isOpen;

	public CephInputStream(String imageName, String poolName, CephInfo info) throws IOException {
		try {
			conn = CephConnectionManager.getConnection(info, poolName);
			rbdImage = conn.getRbd().open(imageName);
			isOpen = true;
			position = 0;
		} catch (Exception e) {
			throw new IOException("Failed to open CephInputStream for image " + imageName + " in pool " + poolName, e);
		}
	}

	@Override
	public int read() throws IOException {
		if (isOpen) {
			byte[] buffer = new byte[1];
			int bytesRead = 0;
			if ((bytesRead = rbdImage.read(position, buffer, buffer.length)) > 0) { // something was read
				position += bytesRead;
				return buffer[0];
			} else { // nothing was read
				return -1;
			}
		} else {
			throw new IOException("Stream is not open/initialized");
		}
	}

	@Override
	public int read(byte[] b, int off, int len) throws NullPointerException, IndexOutOfBoundsException, IOException {
		if (null == b) {
			throw new NullPointerException("Input byte buffer cannot be null");
		}
		if (off < 0 || len < 0 || len > (b.length - off)) {
			throw new IndexOutOfBoundsException("Offset or length cannot be negative. Length cannot be smaller than available size in buffer");
		}

		if (isOpen) {
			byte[] buffer = new byte[len];
			int bytesRead = 0;
			if ((bytesRead = rbdImage.read(position, buffer, buffer.length)) > 0) { // something was read
				position += bytesRead;
				for (int i = 0; i < bytesRead; i++) {
					b[off + i] = buffer[i];
				}
				return bytesRead;
			} else { // nothing was read
				return -1;
			}
		} else {
			throw new IOException("Stream is not open/initialized");
		}
	}

	@Override
	public int read(byte[] b) throws NullPointerException, IOException {
		if (null == b) {
			throw new NullPointerException("Input byte buffer cannot be null");
		}
		return read(b, 0, b.length);
	}

	@Override
	public void close() {
		if (isOpen) {
			try {
				conn.getRbd().close(rbdImage);
			} catch (Exception e) {

			} finally {
				isOpen = false;
				conn.disconnect();
				conn = null;
				rbdImage = null;
			}
		} else {
			// nothing to do here, stream is not open/already closed
		}
	}
}
