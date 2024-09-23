package waffleoRai_extractMyu.mains;

import java.io.IOException;
import java.util.Map;

import waffleoRai_Sound.psx.PSXVAG;
import waffleoRai_Sound.psx.PSXXAAudio;
import waffleoRai_Sound.psx.XAAudioDataOnlyStream;
import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.soundformats.AIFFWriter;
import waffleoRai_SoundSynth.soundformats.WAVWriter;
import waffleoRai_SoundSynth.soundformats.game.XAAudioSampleStream;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_extractMyu.MyuArcCommon;
import waffleoRai_extractMyu.MyuPackagerLogger;

public class PCMMe {
	
	//TODO Dump loop data...
	
	public static void printUsage(){
		System.err.println("MyuPackager ADPCM Decoder ---------- ");
		System.err.println("--input\t\t[Path to input ADPCM file (.vag or. aifc)]");
		System.err.println("--output\t\t[Path to output file (.wav or .aiff - determines format to write from extension)]");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
	}
	
	private static AudioSampleStream readAifc(String inpath) throws IOException, UnsupportedFileTypeException {
		FileBuffer data = FileBuffer.createBuffer(inpath, true);
		XAAudioDataOnlyStream dstr = PSXXAAudio.readAifc(data.getReferenceAt(0L));
		return new XAAudioSampleStream(dstr);
	}
	
	private static AudioSampleStream readVag(String inpath) throws IOException, UnsupportedFileTypeException {
		PSXVAG vag = new PSXVAG(inpath);
		return vag.createSampleStream(false);
	}
	
	private static void writeWav(AudioSampleStream data, String outpath) throws IOException, InterruptedException {
		WAVWriter ww = new WAVWriter(data, outpath);
		while(!data.done()) {
			ww.write(1);
		}
		ww.complete();
	}
	
	private static void writeAiff(AudioSampleStream data, String outpath) throws IOException, InterruptedException {
		AIFFWriter aw = new AIFFWriter(data, outpath);
		while(!data.done()) {
			aw.write(1);
		}
		aw.complete();
	}
	
	public static void main_pcmme(Map<String, String> argmap) throws IOException, UnsupportedFileTypeException, InterruptedException {
		String inPath = MyuArcCommon.getSystemAbsolutePath(argmap.get("input"));
		String outPath = MyuArcCommon.getSystemAbsolutePath(argmap.get("output"));
		
		if(inPath == null) {
			MyuPackagerLogger.logMessage("PCMMe.main_pcmme", 
					"Input file is required!");
			printUsage();
			System.exit(1);
		}
		
		if(!FileBuffer.fileExists(inPath)) {
			MyuPackagerLogger.logMessage("PCMMe.main_pcmme", 
					"Input file \"" + inPath + "\" does not exist!");
			printUsage();
			System.exit(1);
		}
		
		if(outPath == null) {
			//Default to (input).wav
			int lastdot = inPath.lastIndexOf('.');
			outPath = inPath;
			if(lastdot >= 0) {
				outPath = outPath.substring(0, lastdot);
			}
			outPath += ".wav";
			
			MyuPackagerLogger.logMessage("PCMMe.main_pcmme", 
					"Output path not provided. Set to: \"" + outPath + "\"");
		}
		
		//------- Read input -----------
		
		FileBuffer head = FileBuffer.createBuffer(inPath, 0, 0x10, false);
		//Look for AIFC, pGAV or VAGp strings
		AudioSampleStream instr = null;
		long sigpos = head.findString(0, 0x10, "AIFC");
		if(sigpos >= 0L) {
			MyuPackagerLogger.logMessage("PCMMe.main_pcmme", 
					"Input format detected: AIFC");
			instr = readAifc(inPath);
		}
		else {
			sigpos = head.findString(0, 0x10, "pGAV");
			if(sigpos >= 0L) {
				MyuPackagerLogger.logMessage("PCMMe.main_pcmme", 
						"Input format detected: Sony VAG");
				instr = readVag(inPath);
			}
			else {
				MyuPackagerLogger.logMessage("PCMMe.main_pcmme", 
						"ERROR: Input file format not recognized! Quitting...");
				System.exit(1);
			}
		}
		
		String olwr = outPath.toLowerCase();
		if(olwr.endsWith(".aif") || olwr.endsWith(".aiff")) {
			MyuPackagerLogger.logMessage("PCMMe.main_pcmme", 
					"Outputting to AIFF...");
			writeAiff(instr, outPath);
		}
		else {
			MyuPackagerLogger.logMessage("PCMMe.main_pcmme", 
					"Outputting to WAV...");
			writeWav(instr, outPath);
		}
		instr.close();
		
		
	}

}
