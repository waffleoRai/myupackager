package waffleoRai_extractMyu;

import java.io.OutputStream;

public class ImportContext {
	public LiteNode import_specs;
	public OutputStream output;
	public long outpos;
	public int indexInArc;
	
	public String wd;
	
	public int decompSize;
	public int lzMode;
	public boolean matchFlag;
	public LzLitZip litTableZip;

}
