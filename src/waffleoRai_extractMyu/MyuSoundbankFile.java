package waffleoRai_extractMyu;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.sound.midi.InvalidMidiDataException;

import waffleoRai_SeqSound.psx.SEQP;
import waffleoRai_Sound.psx.PSXVAG;
import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.soundformats.AIFFWriter;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_soundbank.vab.PSXVAB;

/*
 * [4] VH Start (Relative to this field)
 * [4] VH End (Relative to field)
 * [4] VB Start (Relative to field)
 * [4n] Seq Offset Table
 * [VAR] Seqs...
 * [VAR] VH...
 * [VAR] VB...
 * 
 * VB Assumed to go to end of file.
 * Of note, there is a lot of padding.
 * It doesn't look like there needs to be padding between the 
 * 	end of the seqs and start of vh.
 * But there does between vh and vb so that vb starts at beginning
 * of CD sector. (Padding is 0x99)
 * 
 */

public class MyuSoundbankFile {
	
	private SEQP[] sequences;
	private PSXVAB vab;
	
	private MyuSoundbankFile(){}
	
	public static MyuSoundbankFile importBinary(FileBuffer rawdata) throws UnsupportedFileTypeException, InvalidMidiDataException, IOException{
		if(rawdata == null) return null;
		
		int vh_off = rawdata.intFromFile(0L);
		int vh_end = rawdata.intFromFile(4L) + 4;
		int vb_off = rawdata.intFromFile(8L) + 8;
		int seq_off = rawdata.intFromFile(12L) + 12;
		
		//Count seqs
		MyuSoundbankFile sbf = new MyuSoundbankFile();
		int seq_count = (seq_off - 12) >>> 2; //# of entries in seq offset table
		int[] seq_locs = null;
		if(seq_count > 0){
			seq_locs = new int[seq_count];
			sbf.sequences = new SEQP[seq_count];
			
			long cpos = 12;
			for(int i = 0; i < seq_count; i++){
				int val = rawdata.intFromFile(cpos);
				if(val == 0) {
					seq_locs[i] = vh_off;
					seq_count = i;
					break;
				}
				else {
					seq_locs[i] = val + (int)cpos;
					cpos += 4;
				}
			}
		}
		
		for(int i = 0; i < seq_count; i++){
			int endpos = vh_off;
			if(i < (seq_count - 1)){
				endpos = seq_locs[i+1];
			}
			if(seq_locs[i] >= endpos) continue;
			FileBuffer sub = rawdata.createReadOnlyCopy(seq_locs[i], endpos);
			sbf.sequences[i] = new SEQP(sub, 0);
			sub.dispose();
		}
		
		FileBuffer vh = rawdata.createReadOnlyCopy(vh_off, vh_end);
		FileBuffer vb = rawdata.createReadOnlyCopy(vb_off, rawdata.getFileSize());
		sbf.vab = new PSXVAB(vh, vb);
		
		return sbf;
	}

	public boolean exportSoundbankTo(String dirpath, LiteNode src_node, LiteNode trg_node){
		//Node in arc xml lists the paths to the seqs to include in arc superbank, and path to xml describing the vab (both head and body)
		
		//Get subfile names
		int seq_count = (sequences != null)?sequences.length:0;
		String[] seq_file_names = null;
		String vab_file_name = "vab_info";
		if(seq_count > 0) {
			seq_file_names = new String[seq_count];
			for(int i = 0; i < seq_count; i++) seq_file_names[i] = String.format("bseq_%03d", i);
		}
		
		if(src_node != null){
			int i = 0;
			for(LiteNode child : src_node.children){
				if(!child.name.equals(MyupkgConstants.XML_NODENAME_SUBFILE)) continue;
				String aval = child.attr.get(MyupkgConstants.XML_ATTR_FILENAME);
				if(aval != null){
					if(i < seq_count){
						seq_file_names[i] = aval;
					}
					else{
						vab_file_name = aval;
						break;
					}	
				}
				i++;
			}
		}
		
		//Dump sequences
		for(int i = 0; i < seq_count; i++){
			LiteNode seqnode = trg_node.newChild(MyupkgConstants.ASSET_TYPE_SEQ);
			String filename = String.format("bseq_%03d", i);
			if(seq_file_names != null){
				if(seq_file_names[i] != null) filename = seq_file_names[i];
			}
			if(sequences[i] == null) continue;
			filename += ".mid";
			seqnode.value = filename;
			
			try {
				sequences[i].writeMIDI(dirpath + File.separator + filename);
			} catch (IOException e) {
				e.printStackTrace();
				MyuPackagerLogger.logMessage("MyuSoundbankFile.exportSoundbankTo", 
						"I/O error occurred exporting sequence \"" + filename + "\"");
				return false;
			}
			
		}
		
		//Dump samples
		String sampledir = dirpath + File.separator + "samples";
		try{
			if(!FileBuffer.directoryExists(sampledir)){
				Files.createDirectories(Paths.get(sampledir));
			}
		}
		catch(Exception ex){
			MyuPackagerLogger.logMessage("MyuSoundbankFile.exportSoundbankTo", 
					"Sample output directory could not be created!");
			return false;
		}
		
		int sample_count = vab.countSamples();
		String[] sample_names = null;
		if(sample_count > 0){
			sample_names = new String[sample_count];
			for(int i = 0; i < sample_count; i++){
				String name = String.format("vb_smpl_%03d", i);
				sample_names[i] = name;
				PSXVAG sample = vab.getSample(i);
				AudioSampleStream str = sample.createSampleStream(false);
				try {
					AIFFWriter aifw = new AIFFWriter(str, sampledir + File.separator + name + ".aiff");
					int frame_count = sample.totalFrames();
					if(sample.loops()){
						int loop_st = sample.getLoopFrame();
						aifw.setLoopPointsMetadata(loop_st, frame_count);
					}
					aifw.write(frame_count);
					aifw.complete();
				} catch (IOException e) {
					MyuPackagerLogger.logMessage("MyuSoundbankFile.exportSoundbankTo", 
							"I/O error occurred exporting sound sample \"" + name + "\"");
					return false;
				} catch (InterruptedException e) {
					MyuPackagerLogger.logMessage("MyuSoundbankFile.exportSoundbankTo", 
							"Interruption exception occurred while exporting sound sample \"" + name + "\"");
					return false;
				}
			}
		}
		
		//Dump VAB as xml (both header and body are in this xml - body block specifies sound sample names and paths)
		LiteNode bnknode = trg_node.newChild(MyupkgConstants.ASSET_TYPE_SOUNDFONT);
		bnknode.value = vab_file_name + ".xml";
		LiteNode vab_root = new LiteNode();
		vab_root.name = SoundbankXML.XML_VABNODE_VAB;
		LiteNode vh = SoundbankXML.exportSoundbankHead(vab, sample_names);
		vab_root.children.add(vh);
		vh.parent = vab_root;
		LiteNode vbnode = vab_root.newChild(SoundbankXML.XML_VABNODE_VB);
		for(int i = 0; i < sample_count; i++){
			LiteNode smplnode = vbnode.newChild(MyupkgConstants.ASSET_TYPE_SOUNDSAMP);
			smplnode.value = "samples/" + sample_names[i] + ".aiff";
			smplnode.attr.put(SoundbankXML.XML_VABATTR_SAMPNAME, sample_names[i]);
		}
		
		//Output XML
		String vabxml_path = dirpath + File.separator + vab_file_name + ".xml";
		try {
			MyuArcCommon.writeXML(vabxml_path, vab_root);
		} catch (IOException e) {
			e.printStackTrace();
			MyuPackagerLogger.logMessage("MyuSoundbankFile.exportSoundbankTo", 
					"I/O error occurred while trying to output VAB XML");
			return false;
		}
		
		return true;
	}
	
	public static class SoundBankHandler implements TypeHandler{

		public boolean exportCallback(ExportContext ctx) {
			//Write as MIDI and update output node
			String filename = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_FILENAME);
			if(filename == null){
				MyuPackagerLogger.logMessage("MyuSeqFile.SoundBankHandler.exportCallback", 
						"File name is required for export!");
				return false;
			}
			
			String ename = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_ENUM);
			if(ename == null){
				//Use file name
				ename = filename.toUpperCase();
				ename = ename.replace(" ", "_");
			}
			ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_ENUM, ename);
			ctx.target_out.name = MyupkgConstants.ASSET_TYPE_BNK;
			ctx.target_out.value = ctx.rel_dir + "/" + filename;
			
			String outdir = ctx.output_dir + File.separator + filename;
			if(!FileBuffer.fileExists(outdir)){
				try {
					Files.createDirectories(Paths.get(outdir));
				} catch (IOException e) {
					e.printStackTrace();
					MyuPackagerLogger.logMessage("MyuSeqFile.SoundBankHandler.exportCallback", 
							"Could not create target directory \"" + outdir + "\"");
					return false;
				}
			}
			
			//Read in.
			MyuSoundbankFile sb = null;
			try {
				sb = MyuSoundbankFile.importBinary(ctx.data);
			} catch (UnsupportedFileTypeException e) {
				e.printStackTrace();
				MyuPackagerLogger.logMessage("MyuSeqFile.SoundBankHandler.exportCallback", 
						"Soundbank binary \"" + filename + "\" or one of its components could not be read.");
			} catch (InvalidMidiDataException e) {
				e.printStackTrace();
				MyuPackagerLogger.logMessage("MyuSeqFile.SoundBankHandler.exportCallback", 
						"There was a MIDI error importing a sequence from soundbank \"" + filename + "\"");
			} catch (IOException e) {
				e.printStackTrace();
				MyuPackagerLogger.logMessage("MyuSeqFile.SoundBankHandler.exportCallback", 
						"I/O Exception caught while trying to read soundbank binary \"" + filename + "\"");
			}
			
			return sb.exportSoundbankTo(outdir, ctx.target_in, ctx.target_out);
		}
		
	}
	
}
