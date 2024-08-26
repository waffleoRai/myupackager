package waffleoRai_extractMyu.tables;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class SymbolList {
	
	public static List<SymbolInfo> readSplatSymbolList(String filePath) throws IOException{
		List<SymbolInfo> list = new LinkedList<SymbolInfo>();
		BufferedReader br = new BufferedReader(new FileReader(filePath));
		String line = null;
		while((line = br.readLine()) != null) {
			if(line.isEmpty()) continue;
			//First split by comment
			String[] spl1 = line.split("//");
			String def = spl1[0].trim();
			String cmt = null;
			if(spl1.length > 1) cmt = spl1[1].trim();
			
			SymbolInfo sym = new SymbolInfo();
			def = def.replace(" ", "");
			def = def.replace(";", "");
			spl1 = def.split("=");
			sym.name = spl1[0];
			if(spl1.length > 1) {
				String astr = spl1[1];
				if(astr.startsWith("0x")) {
					sym.address = Long.parseUnsignedLong(astr.substring(2), 16);
				}
				else sym.address = Long.parseUnsignedLong(astr);
			}
			
			if(cmt != null) {
				spl1 = line.split(" ");
				for(int i = 0; i < spl1.length; i++) {
					String ss = spl1[i].trim();
					String[] spl2 = ss.split(":");
					if(spl2.length < 2) continue;
					String key = spl2[0];
					String val = spl2[1];
					if(key.equals("type")) {
						sym.type = val;
					}
					else if(key.equals("size")) {
						if(val.startsWith("0x")) sym.sizeBytes = Integer.parseUnsignedInt(val.substring(2), 16);
						else sym.sizeBytes = Integer.parseUnsignedInt(val);
					}
				}
			}
			
			list.add(sym);
		}
		br.close();
		return list;
	}

}
