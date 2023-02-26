package waffleoRai_extractMyu;

import waffleoRai_Utils.FileBuffer;

public class ExportContext {
	
	public String rel_dir; //Relative dir path - for xml
	public String output_dir; //Full output dir path on local system
	
	public FileBuffer data;
	public LiteNode target_in;
	public LiteNode target_out;
	
	public int global_flags;
	
}
