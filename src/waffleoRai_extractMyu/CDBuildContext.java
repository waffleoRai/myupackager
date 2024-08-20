package waffleoRai_extractMyu;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import waffleoRai_Containers.CDDateTime;
import waffleoRai_extractMyu.mains.IsoBuild.WTrack;

public class CDBuildContext {
	
	public LiteNode isoInfo;
	public char region;
	
	public int currentSecAbs;
	public int totalSectors; //Must be calculated before the vol descriptor is written
	public CDDateTime volumeTimestamp;
	
	public int ptSize;
	public int[] ptSecs;
	public int baseDirSec;
	
	public boolean matchFlag;
	public boolean buildAllFlag; //If set, rebuild arcs even if built copy is already there
	public List<WTrack> tracks;
	//public List<LiteNode> cdFiles;
	public LiteNode psLogoNode;
	
	public String wd;
	public String input_xml;
	public String output_iso;
	public String checksum_path;
	public String build_dir;
	public String incl_dir; //Where to put h files from arc build
	public String src_dir; //Where to put c files from arc build
	
	public OutputStream out;
	
	public CDBuildContext() {
		ptSecs = new int[4];
		//cdFiles = new ArrayList<LiteNode>(32);
		tracks = new ArrayList<WTrack>(4);
		currentSecAbs = 150;
		totalSectors = 18; //17 is the vol descriptor terminator.
		baseDirSec = 18;
	}

}
