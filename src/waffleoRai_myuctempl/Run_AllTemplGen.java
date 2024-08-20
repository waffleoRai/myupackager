package waffleoRai_myuctempl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Utils.FileBuffer;

public class Run_AllTemplGen {
	
	private static final String SEP = File.separator;
	
	private static class SecInfo{
		public String name;
		public int bssSize;
		public long bssStart;
	}
	
	public static void main(String[] args) {
		String secsTablePath = args[0];
		String buildRootDir = args[1];
		
		try {
			//Read section table
			SecInfo bssLast = null;
			List<SecInfo> seclist = new LinkedList<SecInfo>();
			BufferedReader br = new BufferedReader(new FileReader(secsTablePath));
			String line = br.readLine();
			while((line = br.readLine()) != null) {
				String[] fields = line.split("\t");
				SecInfo sec = new SecInfo();
				seclist.add(sec);
				sec.name = fields[0];
				if(fields.length < 5) continue;
				if(!fields[4].equals("N/A")) {
					sec.bssStart = Long.parseUnsignedLong(fields[4], 16);
					if(bssLast != null) {
						bssLast.bssSize = (int)(sec.bssStart - bssLast.bssStart);
					}
					bssLast = sec;
				}
			}
			br.close();
			
			//Common paths
			String textDir = buildRootDir + SEP + "asm" + SEP + "text";
			String dataDir = buildRootDir + SEP + "asm" + SEP + "data";
			String asmSplitDir = buildRootDir + SEP + "asm" + SEP + "nonmatching";
			String hDir = buildRootDir + SEP + "include" + SEP + "template";
			String cDir = buildRootDir + SEP + "src" + SEP + "template";
			
			String scanDirs = textDir + ";" + dataDir;
			
			//Go through sections. Stop when hit library functions.
			for(SecInfo section : seclist) {
				if(section.name.startsWith("Lib")) break;
				System.out.println("Working on section " + section.name);
				int argCount = 11;
				String textPath = textDir + SEP + section.name + ".s";
				String dataPath = dataDir + SEP + section.name + ".data.s";
				String rodataPath = dataDir + SEP + section.name + ".rodata.s";
				
				boolean hasText = false;
				boolean hasroData = false;
				boolean hasData = false;
				if(FileBuffer.fileExists(textPath)) {argCount += 2; hasText = true;}
				if(FileBuffer.fileExists(dataPath)) {argCount += 2; hasData = true;}
				if(FileBuffer.fileExists(rodataPath)) {argCount += 2; hasroData = true;}
				if(section.bssSize > 0) argCount += 2;
				
				String[] secArgs = new String[argCount];
				int i = 0;
				secArgs[i++] = MCT_Main.MODE_TEMPLGEN_STR;
				secArgs[i++] = "--outh";
				secArgs[i++] = hDir + SEP + section.name + ".h";
				secArgs[i++] = "--outc";
				secArgs[i++] = cDir + SEP + section.name + ".c";
				secArgs[i++] = "--asmoutdir";
				secArgs[i++] = asmSplitDir + SEP + section.name;
				secArgs[i++] = "--updirs";
				secArgs[i++] = scanDirs;
				secArgs[i++] = "--downdirs";
				secArgs[i++] = scanDirs;
				if(hasText) {
					secArgs[i++] = "--text";
					secArgs[i++] = textPath;
				}
				if(hasroData) {
					secArgs[i++] = "--rodata";
					secArgs[i++] = rodataPath;
				}
				if(hasData) {
					secArgs[i++] = "--data";
					secArgs[i++] = dataPath;
				}
				if(section.bssSize > 0) {
					secArgs[i++] = "--bsssize";
					secArgs[i++] = "0x" + Integer.toHexString(section.bssSize);
				}
				
				MCT_Main.main(secArgs);
			}
			
			
		}
		catch(Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		
	}

}
