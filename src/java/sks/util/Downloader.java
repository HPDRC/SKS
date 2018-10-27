/*
 * Downloader.java
 *
 */

package sks.util;

import java.io.*;
import java.net.URL;

public class Downloader {
	private URL remote;
	private File local;
	private long bytesDownloadedCount = 0;
	
	public Downloader(URL remote, File local) {
    this.remote = remote;
    this.local = local;
	}
	
	public void download() throws IOException {
    InputStream in = remote.openStream();
    FileOutputStream out = new FileOutputStream(local);
    int count;
    byte[] bytes = new byte[524288]; // 32768, 524288
    while ((count = in.read(bytes)) > -1) {
      out.write(bytes, 0, count);
      bytesDownloadedCount += count;
    }
    out.close();
    in.close();
	}
	
	public URL getRemote() {
    return remote;
	}
	
	public File getLocal() {
    return local;
	}
	
	public long getBytesDownloadedCount() {
    return bytesDownloadedCount;
	}
} // public class Downloader