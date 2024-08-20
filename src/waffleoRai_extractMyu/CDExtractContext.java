package waffleoRai_extractMyu;

import java.util.List;

import waffleoRai_Containers.ISOXAImage;
import waffleoRai_extractMyu.mains.IsoExtract.RFile;

public class CDExtractContext {

	public String cuePath;
	public String isoPath;
	public ISOXAImage cdImage;
	
	public LiteNode infoRoot;
	public LiteNode currentTrack;
	
	public String wd;
	public String cdOutputDir;
	public String assetOutputDir;
	public String arcSpecDir;
	public String xmlPath;
	public String checksumPath;
	
	public String cdDirRel;
	public String assetDirRel;
	
	public List<RFile> files;
	
}
