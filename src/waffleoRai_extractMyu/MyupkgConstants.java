package waffleoRai_extractMyu;

public class MyupkgConstants {
	
	public static final int PKGR_FLAG_PNGOUT = 0x01;
	
	public static final String XML_ATTR_FILENAME = "FileName";
	public static final String XML_ATTR_ENUM = "Enum";
	public static final String XML_ATTR_FILETYPE = "FileType";
	public static final String XML_ATTR_LZCOMP = "LZComp";
	public static final String XML_ATTR_INDEXTYPE = "IndexingType";
	public static final String XML_ATTR_HASAUDIO = "AudioFlag";
	public static final String XML_ATTR_ASTREAM = "HasXAAudio";
	public static final String XML_ATTR_VSTREAM = "HasXAVideo";
	public static final String XML_ATTR_TILESIZE = "TileSize";
	public static final String XML_ATTR_ACCBYFI = "AccessByFileIndex";
	public static final String XML_ATTR_BITDEPTH = "BitDepth";
	public static final String XML_ATTR_XSCALE = "XFactor";
	public static final String XML_ATTR_YSCALE = "YFactor";
	public static final String XML_ATTR_CLUTIDX = "ClutIndex";
	
	public static final String XML_ATTR_VOLUMEID = "VolId";
	public static final String XML_ATTR_PUBID = "PublisherId";
	public static final String XML_ATTR_FAKETIME = "ForgeTimestamp"; //Use for both created and modified
	public static final String XML_ATTR_REGION = "Region";
	public static final String XML_ATTR_MATCHMODE = "AttemptMatch"; //If set to "True" will use forged timestamps and forced sector starts.
	public static final String XML_ATTR_STARTSEC = "StartSector";
	
	public static final String XML_NODENAME_SUBFILE = "SubFile";
	public static final String XML_NODENAME_ISOBUILD = "IsoBuild";
	public static final String XML_NODENAME_PATHTABLE = "PathTable";
	public static final String XML_NODENAME_PSLOGO = "PSLogo";
	
	public static final String ASSET_TYPE_CLUT = "Clut";
	public static final String ASSET_TYPE_IMAGE = "Image";
	public static final String ASSET_TYPE_IMAGEGROUP = "ImageGroup";
	public static final String ASSET_TYPE_SEQ = "SoundSeq";
	public static final String ASSET_TYPE_BNK = "SoundBank";
	public static final String ASSET_TYPE_SOUNDFONT = "SoundFont";
	public static final String ASSET_TYPE_SOUNDSAMP = "SoundSample";
	public static final String ASSET_TYPE_BIN = "BinFile";
	
	public static final String FTYPE_UNK = "Unknown";
	public static final String FTYPE_SEQ = "Soundseq";
	public static final String FTYPE_SBNK = "Soundbank";
	public static final String FTYPE_IBNK = "Imagebank";
	
	public static final String FILENAME_PSLOGO = "pslogo.bin";

}
