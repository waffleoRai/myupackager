package waffleoRai_extractMyu.mains;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import waffleoRai_DataContainers.MultiValMap;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_extractMyu.MyuPackagerLogger;
import waffleoRai_extractMyu.tables.SymbolInfo;
import waffleoRai_extractMyu.tables.SymbolList;

public class SymbolHist {
	
	private static class SymbolHistory{
		public MultiValMap<Long, String> pastNames;
		public Map<Long, String> currentNames;
		
		public Map<String, String> remap;
		
		public SymbolHistory() {
			pastNames = new MultiValMap<Long, String>();
			currentNames = new HashMap<Long, String>();
			remap = new HashMap<String, String>();
		}
		
		public void updateRemap() {
			remap.clear();
			List<Long> keys = pastNames.getOrderedKeys();
			for(Long key : keys) {
				List<String> past = pastNames.getValues(key);
				String nowName = currentNames.get(key);
				if((nowName != null) && (past != null)) {
					for(String s : past) {
						remap.put(s, nowName);
					}
				}
			}
			
		}
	}
	
	private static SymbolHistory loadHistoryTable(String path) throws IOException {
		//tsv with cols: Addr CurrentName OldNames (semicol delim)
		SymbolHistory hist = new SymbolHistory();
		BufferedReader br = new BufferedReader(new FileReader(path));
		String line = null;
		while((line = br.readLine()) != null) {
			if(line.isEmpty()) continue;
			if(line.startsWith("#")) continue;
			String[] fields = line.split("\t");
			for(int i = 0; i < fields.length; i++) fields[i] = fields[i].trim();
			if(fields[0].startsWith("0x")) fields[0] = fields[0].substring(2);
			long addr = Long.parseUnsignedLong(fields[0], 16);
			String nowname = fields[1];
			hist.currentNames.put(addr, nowname);
			if(fields.length > 2) {
				String[] pastnames = fields[2].split(";");
				for(String s : pastnames) {
					hist.pastNames.addValue(addr, s.trim());
				}
			}
		}
		br.close();
		return hist;
	}
	
	private static void writeHistoryTable(SymbolHistory hist, String path) throws IOException {
		List<Long> keylist = new ArrayList<Long>(hist.currentNames.size()+1);
		keylist.addAll(hist.currentNames.keySet());
		Collections.sort(keylist);
		
		BufferedWriter bw = new BufferedWriter(new FileWriter(path));
		bw.write("#ADDR\tCURRENT_NAME\tOLD_NAMES\n");
		for(Long key : keylist) {
			bw.write(String.format("0x%08x\t", key));
			String name = hist.currentNames.get(key);
			bw.write(name + "\t");
			
			List<String> pastNames = hist.pastNames.getValues(key);
			if(pastNames != null && !pastNames.isEmpty()) {
				boolean first = true;
				for(String s : pastNames) {
					if(!first) bw.write(";");
					first = false;
					bw.write(s);
				}
			}
			
			bw.write("\n");
		}
		bw.close();
	}
	
	private static void updateCodeSymbolsForDir(Path dirpath, SymbolHistory hist, boolean recursive) throws IOException {
		DirectoryStream<Path> dstr = Files.newDirectoryStream(dirpath);
		for(Path p : dstr) {
			if(Files.isDirectory(p)) {
				if(recursive) updateCodeSymbolsForDir(p, hist, recursive);
			}
			else {
				String pstr = p.toAbsolutePath().toString();
				String temppath = pstr + ".tmp";
				
				BufferedWriter bw = new BufferedWriter(new FileWriter(temppath));
				BufferedReader br = new BufferedReader(new FileReader(pstr));
				String line = null;
				while((line = br.readLine()) != null) {
					for(Entry<String, String> entry : hist.remap.entrySet()) {
						line.replace(entry.getKey(), entry.getValue());
					}
					bw.write(line + "\n");
				}
				br.close();
				bw.close();
				
				Files.delete(p);
				Files.move(Paths.get(temppath), p);
			}
		}
		dstr.close();
	}
	
	public static void main_updateCodeSymbols(Map<String, String> argmap) throws IOException {
		String tablePath = argmap.get("symrec");
		String dirPath = argmap.get("dir"); //Root dir of files to scan and replace
		boolean recursive = argmap.containsKey("recursive");
		
		if(!FileBuffer.fileExists(tablePath)) {
			MyuPackagerLogger.logMessage("SymbolHist.main_updateCodeSymbols", "Symbol history file not found!");
			return;
		}
		
		SymbolHistory hist = loadHistoryTable(tablePath);
		hist.updateRemap();
		updateCodeSymbolsForDir(Paths.get(dirPath), hist, recursive);
	}
	
	public static void main_updateSymHistRecord(Map<String, String> argmap) throws IOException {
		String inlist = argmap.get("in");
		String tablePath = argmap.get("symrec");
		
		String[] inpaths = inlist.split(";");
		List<SymbolInfo> inlists = new LinkedList<SymbolInfo>();
		for(String path : inpaths) {
			List<SymbolInfo> list = SymbolList.readSplatSymbolList(path);
			inlists.addAll(list);
		}
		
		SymbolHistory hist = null;
		if(FileBuffer.fileExists(tablePath)) hist = loadHistoryTable(tablePath);
		else hist = new SymbolHistory();
		for(SymbolInfo sym : inlists) {
			String nowname = hist.currentNames.get(sym.address);
			if(nowname != null) {
				if(!nowname.equals(sym.name)) {
					hist.pastNames.addValue(sym.address, nowname);
				}
			}
			hist.currentNames.put(sym.address, sym.name);
		}
		writeHistoryTable(hist, tablePath);
	}

}
