package waffleoRai_extractMyu.tables;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class SectionTable {
	
	private static final int LASTIDX_RODATA = 0;
	private static final int LASTIDX_TEXT = 1;
	private static final int LASTIDX_DATA = 2;
	private static final int LASTIDX_SDATA = 3;
	private static final int LASTIDX_SBSS = 4;
	private static final int LASTIDX_BSS = 5;
	
	private static final String[] SYS_LIBS = {"LIBSN", "LIBAPI", "LIBC", "LIBPRESS", "LIBGPU", "LIBGTE",
			"LIBCD", "LIBETC", "LIBSND", "LIBSPU", "LIBMCRD", "LIBCARD", "LIBPAD", "LIBMATH"};
	
	public static List<Section> readSectionTableTSV(String path) throws IOException{
		List<Section> list = new LinkedList<Section>();
		
		int nameCol = 0;
		int textCol = 1;
		int dataCol = 3;
		int rodataCol = 2;
		int bssCol = 4;
		int sdataCol = 6;
		int sbssCol = 7;
		
		Set<String> syslibs = new HashSet<String>();
		for(String libname : SYS_LIBS) syslibs.add(libname);
		
		BufferedReader br = new BufferedReader(new FileReader(path));
		String line = br.readLine(); //Determine col order
		String[] fields = line.split("\t");
		for(int i = 0; i < fields.length; i++) {
			if(fields[i].equals("Section")) nameCol = i;
			else if(fields[i].equals(".bss")) bssCol = i;
			else if(fields[i].equals(".text")) textCol = i;
			else if(fields[i].equals(".data")) dataCol = i;
			else if(fields[i].equals(".rodata")) rodataCol = i;
			else if(fields[i].equals(".sdata")) sdataCol = i;
			else if(fields[i].equals(".sbss")) sbssCol = i;
		}
		
		Section[] lastSec = new Section[6];
		while((line = br.readLine()) != null) {
			if(line.isEmpty()) continue;
			fields = line.split("\t");
			Section sec = new Section();
			
			if(nameCol >= fields.length) continue;
			String rawname = fields[nameCol].trim();
			if(rawname.contains("::")) {
				String[] nspl = rawname.split("::");
				if(nspl.length > 1) {
					sec.setLibName(nspl[0].trim());
					sec.setName(nspl[1].trim());
					
					String libname = sec.getLibName().toUpperCase();
					if(syslibs.contains(libname)) sec.setIsSys(true);
				}
				else sec.setName(rawname);
			}
			else sec.setName(rawname);
			
			if(textCol < fields.length) {
				String rawstr = fields[textCol].trim();
				if(!rawstr.equals("N/A")) {
					sec.setTextAddr(Long.parseUnsignedLong(rawstr, 16));
					if(lastSec[LASTIDX_TEXT] != null) {
						lastSec[LASTIDX_TEXT].setTextSize((int)(sec.getTextAddr() - lastSec[LASTIDX_TEXT].getTextAddr()));
					}
					lastSec[LASTIDX_TEXT] = sec;
				}
			}
			if(rodataCol < fields.length) {
				String rawstr = fields[rodataCol].trim();
				if(!rawstr.equals("N/A")) {
					sec.setRODataAddr(Long.parseUnsignedLong(rawstr, 16));
					if(lastSec[LASTIDX_RODATA] != null) {
						lastSec[LASTIDX_RODATA].setRODataSize((int)(sec.getRODataAddr() - lastSec[LASTIDX_RODATA].getRODataAddr()));
					}
					lastSec[LASTIDX_RODATA] = sec;
				}
			}
			if(dataCol < fields.length) {
				String rawstr = fields[dataCol].trim();
				if(!rawstr.equals("N/A")) {
					sec.setDataAddr(Long.parseUnsignedLong(rawstr, 16));
					if(lastSec[LASTIDX_DATA] != null) {
						lastSec[LASTIDX_DATA].setDataSize((int)(sec.getDataAddr() - lastSec[LASTIDX_DATA].getDataAddr()));
					}
					lastSec[LASTIDX_DATA] = sec;
				}
			}
			if(bssCol < fields.length) {
				String rawstr = fields[bssCol].trim();
				if(!rawstr.equals("N/A")) {
					sec.setBssAddr(Long.parseUnsignedLong(rawstr, 16));
					if(lastSec[LASTIDX_BSS] != null) {
						lastSec[LASTIDX_BSS].setBssSize((int)(sec.getBssAddr() - lastSec[LASTIDX_BSS].getBssAddr()));
					}
					lastSec[LASTIDX_BSS] = sec;
				}
			}
			if(sdataCol < fields.length) {
				String rawstr = fields[sdataCol].trim();
				if(!rawstr.equals("N/A")) {
					sec.setSDataAddr(Long.parseUnsignedLong(rawstr, 16));
					if(lastSec[LASTIDX_SDATA] != null) {
						lastSec[LASTIDX_SDATA].setSDataSize((int)(sec.getSDataAddr() - lastSec[LASTIDX_SDATA].getSDataAddr()));
					}
					lastSec[LASTIDX_SDATA] = sec;
				}
			}
			if(sbssCol < fields.length) {
				String rawstr = fields[sbssCol].trim();
				if(!rawstr.equals("N/A")) {
					sec.setSBssAddr(Long.parseUnsignedLong(rawstr, 16));
					if(lastSec[LASTIDX_SBSS] != null) {
						lastSec[LASTIDX_SBSS].setSBssSize((int)(sec.getSBssAddr() - lastSec[LASTIDX_SBSS].getSBssAddr()));
					}
					lastSec[LASTIDX_SBSS] = sec;
				}
			}

			list.add(sec);
		}
		
		br.close();
		
		return list;
	}

}
