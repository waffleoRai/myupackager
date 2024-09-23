package waffleoRai_extractMyu;

import waffleoRai_Sound.psx.PSXXAStream;
import waffleoRai_Utils.FileBuffer;

public class ExportContext {
	
	public String rel_dir; //Relative dir path - for xml
	public String output_dir; //Full output dir path on local system
	
	public String arcspec_wd;
	public String xml_wd;
	
	public FileBuffer data;
	public PSXXAStream xaStr;
	public LiteNode target_in;
	public LiteNode target_out;
	
	public int global_flags;
	public int secAlignMode;
	
}
