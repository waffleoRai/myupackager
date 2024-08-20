package waffleoRai_extractMyu;

public class MyupkgConstants {
	
	public static final int PADDING_BYTE_I = 0x99;
	public static final byte PADDING_BYTE = (byte)PADDING_BYTE_I;
	
	public static final int PKGR_FLAG_PNGOUT = 0x01;
	
	public static final String XML_ATTR_FILENAME = "FileName";
	public static final String XML_ATTR_ENUM = "Enum";
	public static final String XML_ATTR_ISXASTR = "XAStream";
	public static final String XML_ATTR_FILETYPE = "FileType";
	public static final String XML_ATTR_LZCOMP = "LZComp";
	public static final String XML_ATTR_INDEXTYPE = "IndexingType";
	public static final String XML_ATTR_HASAUDIO = "AudioFlag";
	public static final String XML_ATTR_ASTREAM = "HasXAAudio";
	public static final String XML_ATTR_VSTREAM = "HasXAVideo";
	public static final String XML_ATTR_SECALIGN = "ForceSectorAlignment";
	public static final String XML_ATTR_SZTBL = "ExplicitSizeTable";
	public static final String XML_ATTR_TILESIZE = "TileSize";
	public static final String XML_ATTR_ACCBYFI = "AccessByFileIndex";
	public static final String XML_ATTR_BITDEPTH = "BitDepth";
	public static final String XML_ATTR_XSCALE = "XFactor";
	public static final String XML_ATTR_YSCALE = "YFactor";
	public static final String XML_ATTR_CLUTIDX = "ClutIndex";
	public static final String XML_ATTR_NAME = "Name";
	public static final String XML_ATTR_CLUT_IMPORT = "ClutImportBehavior";
	public static final String XML_ATTR_CLUT_FORMAT = "ClutFormat";
	public static final String XML_ATTR_BYTE_ORDER = "ByteOrder";
	public static final String XML_ATTR_UNK_FLAGS = "UnkFlags";
	public static final String XML_ATTR_GARBAGE = "BufferGarbage";
	public static final String XML_ATTR_OVRFL = "BufferOverflow";
	public static final String XML_ATTR_FFLTBLPATH = "FFLTablePath";
	public static final String XML_ATTR_LFORCE = "LitTableId";
	public static final String XML_ATTR_UNK05 = "Unk05";
	public static final String XML_ATTR_UNK06 = "Unk06";
	public static final String XML_ATTR_UNK07 = "Unk07";
	public static final String XML_ATTR_USPQN = "MicroSecPerQN";
	public static final String XML_ATTR_TIMESIG = "TimeSignature";
	public static final String XML_ATTR_SEQVER = "SeqPVersion";
	public static final String XML_ATTR_LOOPST = "LoopStart";
	public static final String XML_ATTR_LOOPED = "LoopEnd";
	public static final String XML_ATTR_LOOPCT = "LoopCount";
	
	public static final String XML_ATTR_VOLUMEID = "VolId";
	public static final String XML_ATTR_PUBID = "PublisherId";
	public static final String XML_ATTR_FAKETIME = "ForgeTimestamp"; //Use for both created and modified
	public static final String XML_ATTR_REGION = "Region";
	public static final String XML_ATTR_MATCHMODE = "AttemptMatch"; //If set to "True" will use forged timestamps and forced sector starts.
	public static final String XML_ATTR_STARTSEC = "StartSector";
	public static final String XML_ATTR_CDFILETYPE = "CdFileType";
	public static final String XML_ATTR_FILEPERM = "Permissions";
	public static final String XML_ATTR_OWNERGROUP = "OwnerGroupID";
	public static final String XML_ATTR_OWNERUSER = "OwnerUserID";
	public static final String XML_ATTR_EMBEDTYPE = "EmbedType";
	public static final String XML_ATTR_LEADIN = "LeadIn";
	public static final String XML_ATTR_LEADINGARBAGE = "LeadInGarbage";
	public static final String XML_ATTR_LEADOUT = "LeadOut";
	public static final String XML_ATTR_FILENO = "FileNo";
	public static final String XML_ATTR_MODE = "Mode";
	public static final String XML_ATTR_FORM = "Form";
	
	public static final String XML_NODENAME_SUBFILE = "SubFile";
	public static final String XML_NODENAME_ISOBUILD = "IsoBuild";
	public static final String XML_NODENAME_PATHTABLE = "PathTable";
	public static final String XML_NODENAME_PSLOGO = "PSLogo";
	public static final String XML_NODENAME_CDFILE = "CDFile";
	public static final String XML_NODENAME_CDTRACK = "CDTrack";
	
	public static final String XML_CDFILETYPE_ARC = "Archive"; //If this, then the value points to an xml telling how to build the arc
	public static final String XML_CDFILETYPE_FILE = "File"; //If this, then value points directly to file to include
	
	public static final String XML_EMBEDTYPE_STD = "Standard";
	public static final String XML_EMBEDTYPE_XASTR = "XAStream";
	public static final String XML_EMBEDTYPE_DA = "RawAudio";
	
	public static final String ASSET_TYPE_CLUT = "Clut";
	public static final String ASSET_TYPE_IMAGE = "Image";
	public static final String ASSET_TYPE_IMAGEGROUP = "ImageGroup";
	public static final String ASSET_TYPE_SEQ = "SoundSeq";
	public static final String ASSET_TYPE_BNK = "SoundBank";
	public static final String ASSET_TYPE_SOUNDFONT = "SoundFont";
	public static final String ASSET_TYPE_SOUNDSAMP = "SoundSample";
	public static final String ASSET_TYPE_BIN = "BinFile";
	public static final String ASSET_TYPE_ANIMUNK = "UnkAnime";
	public static final String ASSET_TYPE_CLUTENTRY = "ClutEntry";
	
	public static final String FTYPE_UNK = "Unknown";
	public static final String FTYPE_AUNK = "UnkAnime";
	public static final String FTYPE_SEQ = "Soundseq";
	public static final String FTYPE_SBNK = "Soundbank";
	public static final String FTYPE_IBNK = "Imagebank";
	
	public static final String CLUT_IMPORT_TYPE_SOURCEFILE = "ImageSource";
	public static final String CLUT_IMPORT_TYPE_MATCH = "Match";
	public static final String CLUT_IMPORT_TYPE_GENERATE = "Generate";
	
	public static final String CLUT_FORMAT_ARGB = "BinARGB";
	public static final String CLUT_FORMAT_RGB = "BinRGB";
	public static final String CLUT_FORMAT_TEXT_DEC = "TextDecimalCsv";
	public static final String CLUT_FORMAT_TEXT_HEX = "TextHexcode";
	
	public static final String BYTE_ORDER_BIG_ENDIAN = "BigEndian";
	public static final String BYTE_ORDER_LITTLE_ENDIAN = "LittleEndian";
	
	public static final String COMP_TYPE_NONE = "None";
	public static final String COMP_TYPE_FAST = "Fast";
	public static final String COMP_TYPE_FASTFORCE = "FastForceLit";
	public static final String COMP_TYPE_BEST = "Best";
	
	public static final String FILENAME_PSLOGO = "pslogo.bin";

}
