package waffleoRai_extractMyu;

import waffleoRai_Sound.psx.PSXVAG;
import waffleoRai_Utils.FileBuffer;

public class MyuSoundSample implements Comparable<MyuSoundSample>{
	
	private int index;
	private String name;
	private String relPath;
	
	private PSXVAG sample;
	
	public int getIndex() {return index;}
	public String getName() {return name;}
	public String getRelPath() {return relPath;}
	public PSXVAG getSampleData() {return sample;}
	
	public void setIndex(int val) {index = val;}
	public void setName(String val) {name = val;}
	public void setRelPath(String val) {relPath = val;}
	public void setSampleData(PSXVAG data) {sample = data;}
	
	private boolean readDataAsVAG(String path) {
		try {
			FileBuffer buffer = FileBuffer.createBuffer(path, false);
			sample = new PSXVAG(buffer);
		}
		catch(Exception ex) {
			ex.printStackTrace();
			return false;
		}
		return sample != null;
	}
	
	public boolean loadData(String wd) {
		if(relPath == null || wd == null) return false;
		String abspath = MyuArcCommon.unixRelPath2Local(wd, relPath);
		int idx = abspath.lastIndexOf('.');
		String ext = null;
		String woext = abspath;
		if(idx >= 0) {
			ext = abspath.substring(idx+1);
			woext = abspath.substring(0, idx);
		}
		
		if(ext != null) {
			if(ext.equalsIgnoreCase("vag") || ext.equalsIgnoreCase("vg") || ext.equalsIgnoreCase("vagp")) {
				if(!FileBuffer.fileExists(abspath)) return false;
				return readDataAsVAG(abspath);
			}
			else if(ext.equalsIgnoreCase("wav") || ext.equalsIgnoreCase("wave")) {
				//TODO
				MyuPackagerLogger.logMessage("MyuSoundSample.loadData", "WAV support not yet implemented.");
				return false;
			}
			else if(ext.equalsIgnoreCase("aiff") || ext.equalsIgnoreCase("aif")) {
				//TODO
				MyuPackagerLogger.logMessage("MyuSoundSample.loadData", "AIFF support not yet implemented.");
				return false;
			}
			else {
				//It should have been pre-converted to vag then. Check.
				String altpath = woext + ".vag";
				if(!FileBuffer.fileExists(altpath)) {
					MyuPackagerLogger.logMessage("MyuSoundSample.loadData", "Audio files that are not VAG or PCM encoded WAV or AIFF must be preconverted to VAG before import!");
					return false;
				}
				return readDataAsVAG(altpath);
			}
		}
		
		return false;
	}

	@Override
	public int compareTo(MyuSoundSample o) {
		// TODO Auto-generated method stub
		return 0;
	}

}
