package waffleoRai_extractMyu;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import waffleoRai_Compression.lz77.LZMu;
import waffleoRai_Compression.lz77.LZMu.LZMuDef;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBufferStreamer;
import waffleoRai_Utils.StreamWrapper;
import waffleoRai_Utils.StringUtils;

public class Run_AutoSpecXmls {
	
	private static final String NODETYPE_ARCSPEC = "ArcSpec";
	private static final String NODETYPE_ARCFILE = "ArcFile";
	private static final String NODETYPE_SUBFILE = "SubFile";
	private static final String ATTRKEY_FILENAME = "FileName";
	private static final String ATTRKEY_FILETYPE = "FileType";
	private static final String ATTRKEY_ENUM = "Enum";
	private static final String ATTRKEY_LZCOMP = "LZComp";
	private static final String ATTRKEY_TILESIZE = "TileSize";
	
	private static final int ARCNO_EFFECT = 1;
	private static final int ARCNO_SCREEN = 5;
	private static final int ARCNO_SE = 6;
	
	private static final int FTYPE_UNKNOWN = 0;
	private static final int FTYPE_IMAGEBANK = 1;
	private static final int FTYPE_SOUNDSEQ = 2;
	private static final int FTYPE_SOUNDBANK = 3;
	
	private static final String[] ARC_NAMES = {"ANIME", "EFFECT", "FACE", "FIELD",
				"SCE", "SCREEN", "SE", "UNIT"};
	private static final int[] ARC_FTYPES = {FTYPE_UNKNOWN, FTYPE_IMAGEBANK,
			FTYPE_IMAGEBANK, FTYPE_UNKNOWN, FTYPE_UNKNOWN, FTYPE_IMAGEBANK,
			FTYPE_SOUNDBANK, FTYPE_IMAGEBANK};
	private static final boolean[] ARC_HASLZ = {false, true, true, true,
			false, true, false, true};
	
	private static List<String> findSubfiles(int arc_index, String file_name, FileBuffer file_data){
		if(arc_index == ARCNO_SE){
			long seq_start = file_data.intFromFile(12) + 12;
			
			int scount = (int)((seq_start - 12) >>> 2);
			List<String> mylist = new ArrayList<String>(2 + scount);
			for(int s = 0; s < scount; s++){
				mylist.add(String.format("%s_seq%03d", file_name, s));
			}
			mylist.add(file_name + "_vh");
			mylist.add(file_name + "_vb");
			return mylist;
		}
		else if(ARC_FTYPES[arc_index] == FTYPE_IMAGEBANK){
			//Assumed sprites
			LZMuDef cdef = LZMu.getDefinition();
			StreamWrapper dec = cdef.decompress(new FileBufferStreamer(file_data));
			dec.get(); dec.get();
			int framecount = 0;
			framecount |= dec.getFull() & 0xff;
			framecount |= (dec.getFull() & 0xff) << 8;
			List<String> mylist = new ArrayList<String>(framecount);
			for(int f = 0; f < framecount; f++) mylist.add(String.format("%s_f%03d", file_name, f));
			return mylist;
		}
		
		return null;
	}
	
	private static void doArc(String inpath, String outdir, int index) throws IOException{
		FileBuffer data = FileBuffer.createBuffer(inpath, false);
		
		String nameapp_upper = ARC_NAMES[index].toUpperCase();
		String nameapp_cap = StringUtils.capitalize(ARC_NAMES[index].toLowerCase());
		
		//Read offset table
		long cpos = 0L;
		int first_val = data.intFromFile(0);
		int file_count = first_val >>> 2;
		long[] offsets = new long[file_count];
		for(int i = 0; i < file_count; i++){
			offsets[i] = data.intFromFile(cpos) + cpos;
			cpos += 4;
		}
		
		int digits = Math.max(3, (int)Math.floor(Math.log10(file_count)) + 1);
		System.out.println("Index\tFileName\tEnum\tSubFileCount");
		String outpath = outdir + File.separator + ARC_NAMES[index].toLowerCase() + ".xml";
		BufferedWriter xmlout = new BufferedWriter(new FileWriter(outpath));
		xmlout.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		xmlout.write("<" + NODETYPE_ARCSPEC + ">\n");
		for(int i = 0; i < file_count; i++){
			xmlout.write("\t");
			
			int filesize = 0;
			if(i < (file_count - 1)){
				filesize = (int)(offsets[i+1] - offsets[i]);
			}
			else{
				filesize = (int)(data.getFileSize() - offsets[i]);
			}
			
			if(filesize > 0){
				String filename = String.format("%s_f%0" + digits + "d", nameapp_upper, i);
				String fileenum = String.format("%s_f%0" + digits + "d", nameapp_cap, i);
				xmlout.write("<" + NODETYPE_ARCFILE + " ");
				xmlout.write(ATTRKEY_FILENAME + "=\"" + filename + "\" ");
				xmlout.write(ATTRKEY_FILETYPE + "=\"");
				switch(ARC_FTYPES[index]){
				case FTYPE_UNKNOWN: xmlout.write("Unknown"); break;
				case FTYPE_IMAGEBANK: xmlout.write("Imagebank"); break;
				case FTYPE_SOUNDSEQ: xmlout.write("Soundseq"); break;
				case FTYPE_SOUNDBANK: xmlout.write("Soundbank"); break;
				}
				xmlout.write("\" ");
				
				xmlout.write(ATTRKEY_ENUM + "=\"" + fileenum + "\" ");
				xmlout.write(ATTRKEY_LZCOMP + "=\"");
				if(ARC_HASLZ[index]) xmlout.write("True");
				else xmlout.write("False");
				xmlout.write("\"");
				
				//Does it need tilesize?
				/*
				 * EFFECT 256 - 268
				 * SCREEN all EXCEPT 126 - 157
				 */
				if((index == ARCNO_EFFECT) && (i >= 256) && (i <= 268)){
					xmlout.write(" " + ATTRKEY_TILESIZE + "=\"32,32\"");
				}
				if((index == ARCNO_SCREEN) && ((i < 126) || (i > 157))){
					xmlout.write(" " + ATTRKEY_TILESIZE + "=\"32,32\"");
				}
				
				//Add/count any subfiles
				int subfile_count = 1;
				if(index == ARCNO_SE){
					FileBuffer thisfile = data.createReadOnlyCopy(offsets[i], offsets[i] + filesize);
					List<String> subfiles = findSubfiles(index, filename, thisfile);
					thisfile.dispose();
					
					if(subfiles != null){
						subfile_count = subfiles.size();
						xmlout.write(">\n");
						for(String subfile : subfiles){
							xmlout.write("\t\t<" + NODETYPE_SUBFILE + " ");
							xmlout.write(ATTRKEY_FILENAME + "=\"" + subfile + "\"/>\n");
						}
						xmlout.write("\t</" + NODETYPE_ARCFILE + ">");
					}
					else xmlout.write("/>");
				}
				else if(ARC_FTYPES[index] == FTYPE_IMAGEBANK){
					FileBuffer thisfile = data.createReadOnlyCopy(offsets[i], offsets[i] + filesize);
					List<String> subfiles = findSubfiles(index, filename, thisfile);
					thisfile.dispose();
					if(subfiles != null){
						subfile_count = subfiles.size();
					}
					xmlout.write("/>");
				}
				else{
					xmlout.write("/>");
				}
				
				//Print stdout line
				System.out.println(i + "\t" + filename + "\t" + fileenum + "\t" + subfile_count);
			}
			else{
				xmlout.write("<" + NODETYPE_ARCFILE + "/>");
				System.out.println(i + "\t[Empty]\t[Empty]\t0");
			}

			xmlout.write("\n");
		}
		xmlout.write("</" + NODETYPE_ARCSPEC + ">\n");
		xmlout.close();
	}

	public static void main(String[] args) {

		String indir = args[0];
		String outdir = args[1];
		
		try{
			for(int i = 0; i < ARC_NAMES.length; i++){
				String path = indir + File.separator + "D_" + ARC_NAMES[i] + ".BIN";
				doArc(path, outdir, i);
			}
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
		

	}

}
