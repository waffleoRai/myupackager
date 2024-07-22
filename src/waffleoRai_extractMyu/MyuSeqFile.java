package waffleoRai_extractMyu;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.sound.midi.InvalidMidiDataException;

import waffleoRai_SeqSound.MIDI;
import waffleoRai_SeqSound.psx.SEQP;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class MyuSeqFile {

	/*
	 * Format:
	 * 	[4] Pointer to seq end (relative to this field)
	 * 	[4] Pointer to seq end (relative to this field)
	 *  [4] Pointer to seq end (relative to this field)
	 *  [4] Pointer to seq start (relative to this field)
	 *  [4] (Unknown - usually 0?)
	 *  [VAR] SeqP data
	 *  [VAR] 0x99 padding. Pad to end of CD sector, I think.
	 *  
	 *  These first five fields are prooooobably overwritten by the engine?
	 */
	
	private SEQP sequence;
	
	private MyuSeqFile(){}
	
	public static MyuSeqFile readSoundseq(FileBuffer data) throws UnsupportedFileTypeException, InvalidMidiDataException{
		data.setEndian(false);
		data.setCurrentPosition(0);
		int[] unk_fields = new int[5];
		for(int i = 0; i < 5; i++) unk_fields[i] = data.nextInt();
		int seq_start = unk_fields[3] + 0xc;
		int seq_end = unk_fields[0];
		
		MyuSeqFile seq = new MyuSeqFile();
		
		FileBuffer sub = data.createReadOnlyCopy(seq_start, seq_end);
		seq.sequence = new SEQP(sub, 0L);
		try{sub.dispose();}catch(Exception ex){ex.printStackTrace();}
		
		return seq;
	}
	
	public static MyuSeqFile importMidi(MIDI mid) throws InvalidMidiDataException{
		MyuSeqFile seq = new MyuSeqFile();
		seq.sequence = SEQP.fromMIDI(mid, true);
		return seq;
	}
	
	public int outputToArcStream(OutputStream out) throws IOException, InvalidMidiDataException{
		//Outputs the 5 pointer fields and seq. Relies on outside caller to add padding.
		//Returns # of output bytes
		FileBuffer serseq = sequence.serializeSEQ();
		int seqsize = (int)serseq.getFileSize();
		
		FileBuffer hdr = new FileBuffer(20, false);
		hdr.addToFile(seqsize + 20);
		hdr.addToFile(seqsize + 16);
		hdr.addToFile(seqsize + 12);
		hdr.addToFile(8);
		hdr.addToFile(0);
		
		hdr.writeToStream(out);
		serseq.writeToStream(out);
		
		return seqsize + 20;
	}
	
	public boolean outputRawSeqp(OutputStream out) throws IOException, InvalidMidiDataException{
		//Just the seqp file to disk.
		return sequence.serializeSEQ(out);
	}
	
	public boolean exportMidi(OutputStream out) throws IOException{
		sequence.writeMIDI(out);
		return true;
	}
	
	public boolean exportMidiSingleTrack(OutputStream out) throws IOException{
		sequence.writeMIDISingleTrack(out);
		return true;
	}
	
	public static class SoundSeqHandler implements TypeHandler{
		
		public int importCallback(ImportContext ctx){
			if(ctx == null) return 0;
			
			String filename = ctx.import_specs.value;
			if(filename == null) {
				MyuPackagerLogger.logMessage("MyuSeqFile.SoundSeqHandler.importCallback", "File name is required for import!");
				return 0;
			}
			
			//Read MIDI file
			try {
				int ret = 0;
				FileBuffer data = FileBuffer.createBuffer(filename, true);
				MIDI mid = new MIDI(data);
				MyuSeqFile seqfile = MyuSeqFile.importMidi(mid);
				
				//Metadata
				String aval = ctx.import_specs.attr.get(MyupkgConstants.XML_ATTR_USPQN);
				if(aval != null) {
					seqfile.sequence.setUSPQN(Integer.parseInt(aval));
				}
				else seqfile.sequence.setTempo(120);
				
				aval = ctx.import_specs.attr.get(MyupkgConstants.XML_ATTR_TIMESIG);
				if(aval != null) {
					String[] spl = aval.split(":");
					int n = 4;
					int d = 4;
					if(spl != null) n = Integer.parseInt(spl[0]);
					if(spl != null && spl.length > 1) d = Integer.parseInt(spl[1]);
					seqfile.sequence.setTimeSignature(n, d);
				}
				else seqfile.sequence.setTimeSignature(4, 4);
				
				//Loop points. These will override whatever is in the incoming MIDI.
				aval = ctx.import_specs.attr.get(MyupkgConstants.XML_ATTR_LOOPST);
				if(aval != null) {
					seqfile.sequence.setLoopStart(Long.parseLong(aval));
				}
				
				aval = ctx.import_specs.attr.get(MyupkgConstants.XML_ATTR_LOOPED);
				if(aval != null) {
					seqfile.sequence.setLoopEnd(Long.parseLong(aval));
				}
				
				aval = ctx.import_specs.attr.get(MyupkgConstants.XML_ATTR_LOOPCT);
				if(aval != null) {
					seqfile.sequence.setLoopCount(Integer.parseInt(aval));
				}
				
				ret = seqfile.outputToArcStream(ctx.output);
				return ret;
			}
			catch(Exception ex) {
				ex.printStackTrace();
			}
			
			return 0;
		}

		public boolean exportCallback(ExportContext ctx) {
			//Write as MIDI and update output node
			String filename = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_FILENAME);
			if(filename == null){
				MyuPackagerLogger.logMessage("MyuSeqFile.SoundSeqHandler.exportCallback", "File name is required for export!");
				return false;
			}
			
			String ename = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_ENUM);
			if(ename == null){
				//Use file name
				ename = filename.toUpperCase();
				ename = ename.replace(" ", "_");
			}
			ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_ENUM, ename);
			ctx.target_out.name = MyupkgConstants.ASSET_TYPE_SEQ;
			ctx.target_out.value = ctx.rel_dir + "/" + filename + ".mid";
			
			//Now the actual midi
			String midi_path = ctx.output_dir + File.separator + filename + ".mid";
			try{
				MyuSeqFile me = MyuSeqFile.readSoundseq(ctx.data);
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(midi_path));
				//me.exportMidi(bos);
				me.exportMidiSingleTrack(bos);
				bos.close();
				
				//Extract and save metadata
				int uspqn = me.sequence.getMicrosecondsPerQuarterNote();
				ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_USPQN, Integer.toString(uspqn));
				ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_SEQVER, Integer.toString(me.sequence.getVersion()));
				int n = me.sequence.getTimeSignatureNumerator();
				int d = me.sequence.getTimeSignatureDenominator();
				ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_TIMESIG, n + ":" + d);
			}
			catch(UnsupportedFileTypeException ex){
				MyuPackagerLogger.logMessage("MyuSeqFile.SoundSeqHandler.exportCallback", 
						"Input file could not be parsed as SoundSeq!");
				return false;
			}
			catch(InvalidMidiDataException ex){
				MyuPackagerLogger.logMessage("MyuSeqFile.SoundSeqHandler.exportCallback", 
						"Input sequence contains invalid MIDI data!");
				return false;
			}
			catch(IOException ex){
				MyuPackagerLogger.logMessage("MyuSeqFile.SoundSeqHandler.exportCallback", 
						"There was an I/O error opening the output stream.");
				return false;
			}
			
			
			return true;
		}
		
	}
	
}
