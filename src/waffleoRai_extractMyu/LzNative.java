package waffleoRai_extractMyu;

import waffleoRai_Utils.FileBuffer;

public class LzNative {
	
	private static boolean libLoaded = false;
	
	static {
		//For this to work, the so/dll must be on Java's libpath for this run
		try {
			System.loadLibrary("lzmu");
			libLoaded = true;
		}
		catch(Exception ex) {
			System.err.println("Failed to load lzmu library! See stack trace below.");
			ex.printStackTrace();
		}
	}
	
	private static native byte[] lzCompressMatchC(byte[] inBuffer, int mode);
	private static native byte[] lzCompressMatchCForceLit(byte[] inBuffer, byte[] f);

	public static FileBuffer lzCompressMatch(FileBuffer data, int decSize, int mode) {
		if(!libLoaded) return null;
		//The native compression function will compress everything it sees.
		//It does not add the file size at beginning. Wrapper will need to add it.
		if(data == null || decSize < 1) return null;
		byte[] input = data.getBytes(0, data.getFileSize());
		byte[] encraw = lzCompressMatchC(input, mode);
		
		if(encraw == null) return null;
		FileBuffer out = new FileBuffer(4 + encraw.length, false);
		out.addToFile(decSize);
		for(int i = 0; i < encraw.length; i++) out.addToFile(encraw[i]);
		
		return out;
	}
	
	public static FileBuffer lzCompressMatchForceLit(FileBuffer data, int decSize, FileBuffer table) {
		if(!libLoaded) return null;
		if(data == null || decSize < 1 || table == null) return null;
		byte[] input = data.getBytes(0, data.getFileSize());
		byte[] ltable = table.getBytes(0, table.getFileSize());
		
		byte[] encraw = lzCompressMatchCForceLit(input, ltable);
		if(encraw == null) return null;
		FileBuffer out = new FileBuffer(4 + encraw.length, false);
		out.addToFile(decSize);
		for(int i = 0; i < encraw.length; i++) out.addToFile(encraw[i]);
		
		return out;
	}
	
	public static boolean libLoaded() {
		return libLoaded;
	}
	
}
