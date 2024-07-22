package waffleoRai_extractMyu;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import waffleoRai_Utils.FileBuffer;

public class LzLitZip {
	
	private ZipFile zip;
	private ZipEntry[] entries;
	
	public LzLitZip(String path) throws IOException {
		zip = new ZipFile(path);
		readEntries();
	}
	
	private void readEntries() {
		Enumeration<? extends ZipEntry> elist = zip.entries();
		
		int maxno = 0;
		List<ZipEntry> found = new LinkedList<ZipEntry>();
		while(elist.hasMoreElements()) {
			ZipEntry entry = elist.nextElement();
			String name = entry.getName();
			if(!name.contains("_")) continue;
			name = name.substring(0, name.indexOf('_'));
			try {
				int no = Integer.parseInt(name);
				if(no > maxno) maxno = no;
				found.add(entry);
			}
			catch(NumberFormatException ex) {
				continue;
			}
		}
		
		if(maxno < 1) return;
		entries = new ZipEntry[maxno + 1];
		for(ZipEntry e : found) {
			String name = e.getName();
			name = name.substring(0, name.indexOf('_'));
			int no = Integer.parseInt(name);
			entries[no] = e;
		}
	}
	
	public FileBuffer getEntryData(int id) throws IOException {
		if(entries == null) return null;
		if(id < 0 || id >= entries.length) return null;
		if(entries[id] == null) return null;
		
		int decSize = (int)entries[id].getSize();
		InputStream is = zip.getInputStream(entries[id]);
		FileBuffer buffer = new FileBuffer(decSize, false);
		for(int i = 0; i < decSize; i++) {
			buffer.addToFile((byte)is.read());
		}
		is.close();
		
		return buffer;
	}
	
	public void close() throws IOException {
		if(zip != null) zip.close();
		zip = null;
	}

}
