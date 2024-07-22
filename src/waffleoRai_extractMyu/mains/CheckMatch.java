package waffleoRai_extractMyu.mains;

import java.util.Map;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_extractMyu.MyuArcCommon;
import waffleoRai_extractMyu.MyuPackagerLogger;

public class CheckMatch {
	
	private String refFile;
	private String trgFile;
	private String outStem;
	private boolean xaFlag = false;
	private boolean lzFlag = false;
	
	private boolean checkArgs() {
		if(refFile == null){
			MyuPackagerLogger.logMessage("CheckMatch.checkArgs", 
					"Reference file is required input!");
			return false;
		}
		
		if(trgFile == null){
			MyuPackagerLogger.logMessage("CheckMatch.checkArgs", 
					"Target file is required input!");
			return false;
		}
		
		if(!FileBuffer.fileExists(refFile)) {
			MyuPackagerLogger.logMessage("CheckMatch.checkArgs", 
					"Reference file \"" + refFile + "\" does not exist!");
			return false;
		}
		
		if(!FileBuffer.fileExists(trgFile)) {
			MyuPackagerLogger.logMessage("CheckMatch.checkArgs", 
					"Target file \"" + refFile + "\" does not exist!");
			return false;
		}
		
		
		return true;
	}
	
	private long compareFiles(FileBuffer ref, FileBuffer trg) {
		//TODO
		//Return offset of first difference. -1L means same.
		return 0L;
	}
	
	private void checkRegularArchive() {
		try {
			FileBuffer reffile = FileBuffer.createBuffer(refFile, false);
			long[] refTable = MyuArcCommon.readOffsetTable(reffile);
			if(refTable == null) {
				MyuPackagerLogger.logMessage("CheckMatch.checkRegularArchive", 
						"File table could not be read from reference archive!");
				return;
			}
			
			FileBuffer trgfile = FileBuffer.createBuffer(trgFile, false);
			long[] trgTable = MyuArcCommon.readOffsetTable(trgfile);
			if(trgTable == null) {
				MyuPackagerLogger.logMessage("CheckMatch.checkRegularArchive", 
						"File table could not be read from query archive!");
				return;
			}
			
			System.out.println("Reference Archive File Count: " + refTable.length);
			System.out.println("Query Archive File Count: " + trgTable.length);
			if(refTable.length == trgTable.length) {
				System.out.println("[O] File counts match");
				boolean mismatch = false;
				int i = -1;
				for(i = 0; i < trgTable.length; i++) {
					if(refTable[i] != trgTable[i]) {
						mismatch = true;
						break;
					}
				}
				if(mismatch) {
					System.out.println("[X] Offset tables do not match. First mismatch index: " + i);
				}
				else System.out.println("[O] Offset tables match");
			}
			else {
				System.out.println("[X] File counts do not match!");
			}
			
			int fcount = trgTable.length;
			if(refTable.length < fcount) fcount = refTable.length;
			for(int i = 0; i < fcount; i++) {
				long rSt = refTable[i];
				long tSt = trgTable[i];
				long rSz = (i < (fcount - 1))?(refTable[i+1] - rSt):(reffile.getFileSize() - rSt);
				long tSz = (i < (fcount - 1))?(trgTable[i+1] - tSt):(trgfile.getFileSize() - tSt);
				if(rSz <= 0 && tSz <= 0) continue;
				
				System.out.println(">> FILE " + i + "/" + fcount + "...");
				if(rSz == tSz) {
					System.out.println("\t[O] File sizes match: 0x" + Long.toHexString(rSz));
				}
				else {
					System.out.println("\t[X] File sizes do not match: Ref = 0x" + Long.toHexString(rSz) + ", Query = 0x" + Long.toHexString(tSz) );
				}
				
				if(rSz <= 0 || tSz <= 0) {
					System.out.println("\t[X] One file is empty, and the other is not.");
				}
				else {
					System.out.println("\t[O] Both files are non-empty");
				}
				
				FileBuffer rSub = reffile.createReadOnlyCopy(rSt, rSt + rSz);
				FileBuffer tSub = trgfile.createReadOnlyCopy(tSt, tSt + tSz);
				
				//Test these as-is
				boolean mismatch = false;
				long testSz = (rSz < tSz) ? rSz:tSz;
				for(long pos = 0; pos < testSz; pos++) {
					if(rSub.getByte(pos) != tSub.getByte(pos)) {
						mismatch = true;
						System.out.println("\t[X] On-disk files do not match. First mismatch: 0x" + Long.toHexString(pos));
						if(outStem != null) {
							String outpath = outStem + String.format("_subfile_%03d_disk_ref.bin", i);
							rSub.writeFile(outpath);
							outpath = outStem + String.format("_subfile_%03d_disk_query.bin", i);
							tSub.writeFile(outpath);
						}
						break;
					}
				}
				if(!mismatch) {
					System.out.println("\t[O] All bytes checked in on-disk files match!");
				}
				
				//If applicable, decompress and test decompressed versions...
				if(lzFlag) {
					FileBuffer rDec = MyuArcCommon.lzDecompress(rSub);
					FileBuffer tDec = MyuArcCommon.lzDecompress(tSub);
					
					long rdSz = rDec.getFileSize();
					long tdSz = tDec.getFileSize();
					testSz = (rdSz < tdSz) ? rdSz:tdSz;
					mismatch = false;
					for(long pos = 0; pos < testSz; pos++) {
						if(rDec.getByte(pos) != tDec.getByte(pos)) {
							mismatch = true;
							System.out.println("\t[X] Decompressed files do not match. First mismatch: 0x" + Long.toHexString(pos));
							if(outStem != null) {
								String outpath = outStem + String.format("_subfile_%03d_mem_ref.bin", i);
								rDec.writeFile(outpath);
								outpath = outStem + String.format("_subfile_%03d_mem_query.bin", i);
								tDec.writeFile(outpath);
							}
							break;
						}
					}
					if(!mismatch) {
						System.out.println("\t[O] All bytes checked in decompressed files match!");
					}
					
					//Print any excess
					if(rdSz > tdSz) {
						byte[] excess = rDec.getBytes(tdSz, rdSz);
						String excessStr = MyuArcCommon.bufferGarbageData2String(excess);
						System.out.println("\tBufferOverflow=\"" + excessStr + "\"");
					}
					
					rDec.dispose();
					tDec.dispose();
				}
				
				rSub.dispose();
				tSub.dispose();
			}
			
		}
		catch(Exception ex) {
			ex.printStackTrace();
		}
	}
	
	private void checkXAStream() {
		//TODO
	}
	
	private void checkBinaryFile() {
		//TODO
	}
	
	//-------------
	
	public static void main_matchCheck(Map<String, String> args) {
		//TODO
		CheckMatch ctx = new CheckMatch();
		ctx.refFile = args.get("ogfile");
		ctx.trgFile = args.get("myfile");
		ctx.outStem = args.get("outstem");
		ctx.xaFlag = args.containsKey("xa");
		ctx.lzFlag = args.containsKey("lz");
		
		if (!ctx.checkArgs()) return;
		
		if(ctx.xaFlag) {
			ctx.checkXAStream();
		}
		else {
			ctx.checkRegularArchive();
		}
		
		
	}

}
