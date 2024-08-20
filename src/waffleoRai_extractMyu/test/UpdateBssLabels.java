package waffleoRai_extractMyu.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedList;
import java.util.List;

public class UpdateBssLabels {
	
	private static class SymbolRec{
		public String name;
		public long addr;
		public int size = 0;
		public String module;
		public String prefix;
	}

	public static void main(String[] args) {
		String inpath = args[0];
		String outpath = args[1];
		
		final long bssEnd = 0x801fa000L;
		
		try {
			List<SymbolRec> records = new LinkedList<SymbolRec>();
			BufferedReader br = new BufferedReader(new FileReader(inpath));
			String line = null;
			while((line = br.readLine()) != null) {
				if(line.isEmpty()) continue;
				
				SymbolRec rec = new SymbolRec();
				records.add(rec);
				String[] spl = line.split("\t");
				rec.name = spl[0];
				rec.addr = Long.parseUnsignedLong(spl[1], 16);
				rec.module = spl[5];
				rec.prefix = spl[6];
			}
			br.close();
			
			//Calculate sizes and update names
			SymbolRec lastrec = null;
			for(SymbolRec rec : records) {
				String defoName = String.format("D_%08X", rec.addr);
				if(rec.name.equals(defoName)) {
					int addrShort = (int)(rec.addr & 0xffffff);
					rec.name = String.format("D_%s_%x", rec.prefix.toLowerCase(), addrShort);
				}
				if(lastrec != null) {
					lastrec.size = (int)(rec.addr - lastrec.addr);
				}
				lastrec = rec;
			}
			if(lastrec != null) {
				lastrec.size = (int)(bssEnd - lastrec.addr);
			}
			
			//Output
			BufferedWriter bw = new BufferedWriter(new FileWriter(outpath));
			for(SymbolRec rec : records) {
				bw.write(rec.name + "\t");
				bw.write(String.format("%08x\t", rec.addr));
				bw.write("u8\t");
				bw.write("0x" + Integer.toHexString(rec.size) + "\t");
				bw.write(".bss\t");
				bw.write(rec.module + "\t");
				bw.write(rec.prefix + "\n");
			}
			bw.close();
			
		}
		catch(Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		
		
	}

}
