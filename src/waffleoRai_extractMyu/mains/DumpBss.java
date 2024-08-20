package waffleoRai_extractMyu.mains;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_extractMyu.Main;
import waffleoRai_extractMyu.MyuPackagerLogger;
import waffleoRai_extractMyu.tables.Section;
import waffleoRai_extractMyu.tables.SectionTable;

public class DumpBss {
	
	private String secTblPath;
	private String symListPath;
	private String outpath;
		
	private static class SymbolInfo implements Comparable<SymbolInfo>{
		public String name;
		//public String type;
		public long addr;
		public int sizeBytes;
		
		public int compareTo(SymbolInfo o) {
			if(o == null) return 1;
			return (int)(this.addr - o.addr);
		}
	}
	
	private static long parseAddressValue(String input) {
		if(input == null) return -1L;
		if(input.startsWith("0x")) {
			return Long.parseUnsignedLong(input.substring(2), 16);
		}
		
		return Long.parseUnsignedLong(input);
	}
	
	private static int parseIntValue(String input) {
		if(input == null) return -1;
		if(input.startsWith("0x")) {
			return Integer.parseUnsignedInt(input.substring(2), 16);
		}
		
		return Integer.parseUnsignedInt(input);
	}

	private static boolean checkArgs(Map<String, String> argmap, DumpBss ctx) throws IOException {
		ctx.secTblPath = argmap.get("sectbl");
		ctx.symListPath = argmap.get("dsymbols");
		ctx.outpath = argmap.get("asmdir");
		
		if(ctx.secTblPath == null) {
			MyuPackagerLogger.logMessage("DumpBss.checkArgs", "Section table path is required!");
			return false;
		}
		
		if(!FileBuffer.fileExists(ctx.secTblPath)) {
			MyuPackagerLogger.logMessage("DumpBss.checkArgs", "Provided section table file \"" + ctx.secTblPath + "\" does not exist!");
			return false;
		}
		
		if(ctx.symListPath == null) {
			MyuPackagerLogger.logMessage("DumpBss.checkArgs", "WARNING: Symbol list not provided.");
		}
		
		if(!FileBuffer.fileExists(ctx.symListPath)) {
			MyuPackagerLogger.logMessage("DumpBss.checkArgs", "WARNING: Provided symbol list file \"" + ctx.symListPath + "\" does not exist!");
		}
		
		if(ctx.outpath == null) {
			MyuPackagerLogger.logMessage("DumpBss.checkArgs", "WARNING: Output path was not provided.");
			ctx.outpath = ctx.secTblPath.substring(0, ctx.secTblPath.lastIndexOf(File.separator)) + File.separator + "asm";
			MyuPackagerLogger.logMessage("DumpBss.checkArgs", "Output directory set to \"" + ctx.outpath + "\"");
		}
		
		if(!FileBuffer.directoryExists(ctx.outpath)) {
			MyuPackagerLogger.logMessage("DumpBss.checkArgs", "Output directory \"" + ctx.outpath + "\" does not exist. Creating...");
			Files.createDirectories(Paths.get(ctx.outpath));
		}
		
		return true;
	}
	
	public static void main_dumpbss(Map<String, String> argmap) throws IOException {
		DumpBss ctx = new DumpBss();
		if(!checkArgs(argmap, ctx)) {
			Main.printUsage_DumpBss();
			System.exit(1);
		}
		
		//Read section table
		//Expects tsv with columns:
		//	SectionName	.text	.rodata	.data	.bss
		//The latter are virtual addresses in hex pointing to the start of each subsection for the section
		//.text, .rodata, and .data addresses are ignored for this tool
		List<Section> sections = SectionTable.readSectionTableTSV(ctx.secTblPath);
		Collections.sort(sections);
		MyuPackagerLogger.logMessage("DumpBss.main_dumpbss", sections.size() + " sections read!");
		
		//Read in symbol table, if present
		LinkedList<SymbolInfo> symbols = new LinkedList<SymbolInfo>();
		if((ctx.symListPath != null) && FileBuffer.fileExists(ctx.symListPath)) {
			BufferedReader br = new BufferedReader(new FileReader(ctx.symListPath));
			String line = null;
			while((line = br.readLine()) != null) {
				String[] spl1 = line.split("//");
				//Name and address on the left
				SymbolInfo sym = new SymbolInfo();
				if(spl1[0].isEmpty()) continue;
				String[] spl2 = spl1[0].split("=");
				sym.name = spl2[0].trim();
				
				String numstr = spl2[1].replace(";", "");
				numstr = numstr.trim();
				sym.addr = parseAddressValue(numstr);
				
				//Look for type and size on the right.
				if(spl1.length > 1) {
					spl1[1] = spl1[1].trim();
					spl2 = spl1[1].split(" ");
					for(int i = 0; i < spl2.length; i++) {
						spl2[i] = spl2[i].trim();
						String[] spl3 = spl2[i].split(":");
						if(spl3[0].equals("type")) {
							//sym.type = spl3[1].trim();
						}
						else if(spl3[0].equals("size")) {
							sym.sizeBytes = parseIntValue(spl3[1].trim());
						}
					}
				}
				else {
					sym.sizeBytes = 4;
					//sym.type = "u32";
				}
				
				symbols.add(sym);
			}
			br.close();
		}
		Collections.sort(symbols);
		MyuPackagerLogger.logMessage("DumpBss.main_dumpbss", symbols.size() + " symbols read!");
		LinkedList<SymbolInfo> sbssq = new LinkedList<SymbolInfo>(); //Lazy way, but eh.
		sbssq.addAll(symbols);
		
		//Export sections
		//Mimics MM decomp formatting
		for(Section sec : sections) {
			long bssAddr = sec.getBssAddr();
			long sbssAddr = sec.getSBssAddr();
			String name = sec.getName();
			if(bssAddr <= 0L && sbssAddr <= 0L) continue;
			if(!sec.isSys() && name.equals("END")) break;
			String datadir = ctx.outpath + File.separator + "data";
			
			//System lib?
			if(sec.isSys()) {
				datadir += File.separator + "psx";
			}
			String libname = sec.getLibName();
			if(libname != null && !libname.isEmpty()) {
				datadir += File.separator + libname;
			}
			String bsspath = datadir + File.separator + name + ".bss.s";
			String sbsspath = datadir + File.separator + name + ".sbss.s";
			
			if(bssAddr > 0L) {
				MyuPackagerLogger.logMessage("DumpBss.main_dumpbss", "Working on " + bsspath);
				
				BufferedWriter bw = new BufferedWriter(new FileWriter(bsspath));
				bw.write(".include \"macro.inc\"\n\n");
				bw.write(".set noat\n");
				bw.write(".set noreorder\n\n");
				bw.write(".section .bss\n\n");
				bw.write(".balign 4\n\n");
				
				//Look for first symbol
				long symaddr = 0;
				long nowaddr = bssAddr;
				long secend = bssAddr + sec.getBssSize();
				SymbolInfo currentSymbol = null;
				while(symaddr < nowaddr) {
					if(symbols.isEmpty()) {
						currentSymbol = null;
						break;
					}
					currentSymbol = symbols.pop();
					symaddr = currentSymbol.addr;
				}
				
				//While symbols are in range...
				while(currentSymbol != null) {
					if(nowaddr < currentSymbol.addr) {
						bw.write(String.format("glabel D_%08X\n", nowaddr));
						bw.write(String.format("/* %06X %08X */ ", (nowaddr - bssAddr), nowaddr));
						bw.write(String.format(".space 0x%X\n\n", (currentSymbol.addr - nowaddr)));
					}
					
					bw.write(String.format("glabel %s\n", currentSymbol.name));
					bw.write(String.format("/* %06X %08X */ ", (currentSymbol.addr - bssAddr), currentSymbol.addr));
					bw.write(String.format(".space 0x%X\n\n", currentSymbol.sizeBytes));
					nowaddr = currentSymbol.addr + currentSymbol.sizeBytes;
					
					//Go to next symbol
					if(symbols.isEmpty()) currentSymbol = null;
					else {
						currentSymbol = symbols.pop();
						symaddr = currentSymbol.addr;
						
						if(symaddr >= secend) {
							//Put it back
							symbols.push(currentSymbol);
							currentSymbol = null;
						}
					}
				}
				
				//Pad to end
				if(nowaddr < secend) {
					bw.write(String.format("glabel D_%08X\n", nowaddr));
					bw.write(String.format("/* %06X %08X */ ", (nowaddr - bssAddr), nowaddr));
					bw.write(String.format(".space 0x%X\n", (secend - nowaddr)));
				}
				bw.close();	
			}
			
			
			//Now do .sbss, if present
			if(sbssAddr > 0L) {
				MyuPackagerLogger.logMessage("DumpBss.main_dumpbss", "Working on " + sbsspath);
				
				BufferedWriter bw = new BufferedWriter(new FileWriter(sbsspath));
				bw.write(".include \"macro.inc\"\n\n");
				bw.write(".set noat\n");
				bw.write(".set noreorder\n\n");
				bw.write(".section .sbss\n\n");
				bw.write(".balign 4\n\n");
				
				//Look for first symbol
				long symaddr = 0;
				long nowaddr = sbssAddr;
				long secend = sbssAddr + sec.getSBssSize();
				SymbolInfo currentSymbol = null;
				while(symaddr < nowaddr) {
					if(sbssq.isEmpty()) {
						currentSymbol = null;
						break;
					}
					currentSymbol = sbssq.pop();
					symaddr = currentSymbol.addr;
				}
				
				//While symbols are in range...
				while(currentSymbol != null) {
					if(nowaddr < currentSymbol.addr) {
						bw.write(String.format("glabel D_%08X\n", nowaddr));
						bw.write(String.format("/* %06X %08X */ ", (nowaddr - sbssAddr), nowaddr));
						bw.write(String.format(".space 0x%X\n\n", (currentSymbol.addr - nowaddr)));
					}
					
					bw.write(String.format("glabel %s\n", currentSymbol.name));
					bw.write(String.format("/* %06X %08X */ ", (currentSymbol.addr - sbssAddr), currentSymbol.addr));
					bw.write(String.format(".space 0x%X\n\n", currentSymbol.sizeBytes));
					nowaddr = currentSymbol.addr + currentSymbol.sizeBytes;
					
					//Go to next symbol
					if(sbssq.isEmpty()) currentSymbol = null;
					else {
						currentSymbol = sbssq.pop();
						symaddr = currentSymbol.addr;
						
						if(symaddr >= secend) {
							//Put it back
							sbssq.push(currentSymbol);
							currentSymbol = null;
						}
					}
				}
				
				//Pad to end
				if(nowaddr < secend) {
					bw.write(String.format("glabel D_%08X\n", nowaddr));
					bw.write(String.format("/* %06X %08X */ ", (nowaddr - sbssAddr), nowaddr));
					bw.write(String.format(".space 0x%X\n", (secend - nowaddr)));
				}
				bw.close();	
			}
			
		}
		
	}

}
