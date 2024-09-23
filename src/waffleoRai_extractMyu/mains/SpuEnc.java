package waffleoRai_extractMyu.mains;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;

import waffleoRai_Sound.AiffFile;
import waffleoRai_Sound.WAV;
import waffleoRai_Sound.psx.PSXVAG;
import waffleoRai_Sound.psx.PSXVAGCompressor;
import waffleoRai_Sound.psx.PSXXAAudio;
import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_extractMyu.MyuArcCommon;
import waffleoRai_extractMyu.MyuPackagerLogger;

public class SpuEnc {
	
	//TODO Add ability to specify loops...

	
	public static void printUsage(){
		System.err.println("MyuPackager ADPCM Decoder ---------- ");
		System.err.println("--input\t\t[Path to input PCM audio file (.wav or .aiff)]");
		System.err.println("--output\t\t[Path to output file (.vag or .aifc - determines format to write from extension)]");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
	}
	
	private static AudioSampleStream readWav(String inpath) throws IOException, UnsupportedFileTypeException {
		WAV wav = new WAV(inpath);
		return wav.createSampleStream(false);
	}
	
	private static AudioSampleStream readAiff(String inpath) throws IOException, UnsupportedFileTypeException {
		FileBuffer dat = FileBuffer.createBuffer(inpath, true);
		AiffFile aiff = AiffFile.readAiff(dat);
		return aiff.createSampleStream(false);
	}
	
	private static short[][] getSamples(AudioSampleStream data) throws InterruptedException {
		int cCount = data.getChannelCount();
		
		LinkedList<int[][]> cache = new LinkedList<int[][]>();
		int[][] block = new int[cCount][44100];
		int bpos = 0;
		int frames = 0;
		int bd = data.getBitDepth();
		
		while(!data.done()) {
			int[] allSamp = data.nextSample();
			for(int c = 0; c < cCount; c++) {
				int ss = allSamp[c];
				switch(bd) {
				case 8:
					ss -= 0x80;
					ss <<= 8;
					break;
				case 24:
					ss >>= 8;
					break;
				}
				block[c][bpos] = ss;	
			}
			if(++bpos >= block.length) {
				cache.add(block);
				block = new int[cCount][44100];
				bpos = 0;
			}
			frames++;
		}
		
		int f = 0;
		short[][] samp = new short[cCount][frames];
		while(!cache.isEmpty()) {
			int[][] nblock = cache.pop();
			for(int c = 0; c < cCount; c++) {
				for(int i = 0; i < nblock.length; i++) {
					samp[c][f++] = (short)nblock[c][i];
				}
			}
		}
		if(bpos > 0) {
			for(int c = 0; c < cCount; c++) {
				for(int i = 0; i < bpos; i++) {
					samp[c][f++] = (short)block[c][i];
				}
			}
		}
		
		return samp;
	}
	
	private static void writeAifc(AudioSampleStream data, String outpath) throws IOException, InterruptedException {
		int sampleRate = (int)data.getSampleRate();
		short[][] sAll = getSamples(data);
		
		byte[] adpcm = PSXVAGCompressor.encodeXA(sAll, PSXVAG.ENCMODE_NORMAL);
		PSXXAAudio.writeAifc(adpcm, outpath, sampleRate, data.getBitDepth(), data.getChannelCount());
	}
	
	private static void writeVag(AudioSampleStream data, String outpath) throws IOException, InterruptedException {
		//Cache incoming samples since don't know how many
		short[][] sAll = getSamples(data);
		short[] samples = sAll[0];
		
		byte[] adpcm = PSXVAGCompressor.encode(samples, PSXVAG.ENCMODE_NORMAL, -1, -1);
		
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outpath));
		PSXVAG.writeVAGFromRawData(bos, (int)data.getSampleRate(), adpcm);
		bos.close();
	}
	
	public static void main_spuenc(Map<String, String> argmap) throws IOException, InterruptedException, UnsupportedFileTypeException {
		String inPath = MyuArcCommon.getSystemAbsolutePath(argmap.get("input"));
		String outPath = MyuArcCommon.getSystemAbsolutePath(argmap.get("output"));
		
		if(inPath == null) {
			MyuPackagerLogger.logMessage("SpuEnc.main_spuenc", 
					"Input file is required!");
			printUsage();
			System.exit(1);
		}
		
		if(!FileBuffer.fileExists(inPath)) {
			MyuPackagerLogger.logMessage("SpuEnc.main_spuenc", 
					"Input file \"" + inPath + "\" does not exist!");
			printUsage();
			System.exit(1);
		}
		
		if(outPath == null) {
			//Default to (input).vag
			int lastdot = inPath.lastIndexOf('.');
			outPath = inPath;
			if(lastdot >= 0) {
				outPath = outPath.substring(0, lastdot);
			}
			outPath += ".vag";
			
			MyuPackagerLogger.logMessage("SpuEnc.main_spuenc", 
					"Output path not provided. Set to: \"" + outPath + "\"");
		}
		
		FileBuffer head = FileBuffer.createBuffer(inPath, 0, 0x10, false);
		AudioSampleStream instr = null;
		long sigpos = head.findString(0, 0x10, "AIFF");
		if(sigpos >= 0L) {
			MyuPackagerLogger.logMessage("SpuEnc.main_spuenc", 
					"Input format detected: AIFF");
			instr = readAiff(inPath);
		}
		else {
			sigpos = head.findString(0, 0x10, "WAVE");
			if(sigpos >= 0L) {
				MyuPackagerLogger.logMessage("SpuEnc.main_spuenc", 
						"Input format detected: WAV");
				instr = readWav(inPath);
			}
			else {
				MyuPackagerLogger.logMessage("SpuEnc.main_spuenc", 
						"ERROR: Input file format not recognized! Quitting...");
				System.exit(1);
			}
		}
		
		//Check input sample rate and throw warning if needed.
		int sampleRate = (int)instr.getSampleRate();
		
		String olwr = outPath.toLowerCase();
		if(olwr.endsWith(".aifc")) {
			MyuPackagerLogger.logMessage("SpuEnc.main_spuenc", 
					"Outputting to AIFC...");
			if((sampleRate != 37800) && (sampleRate != 18900)) {
				MyuPackagerLogger.logMessage("SpuEnc.main_spuenc", 
						"WARNING: XA Audio sample rate is 37800Hz or 18900Hz, but input is " + sampleRate + "Hz. Pitch issues possible.");
			}
			writeAifc(instr, outPath);
		}
		else {
			MyuPackagerLogger.logMessage("SpuEnc.main_spuenc", 
					"Outputting to VAG...");
			int chCount = instr.getChannelCount();
			if(chCount > 1) {
				MyuPackagerLogger.logMessage("SpuEnc.main_spuenc", 
						"ERROR: VAG converter only accepts mono audio! Please pre-split channels. Quitting...");
				System.exit(2);
			}
			if(sampleRate != 44100) {
				MyuPackagerLogger.logMessage("SpuEnc.main_spuenc", 
						"WARNING: Sample playback rate is 44100Hz, but input is " + sampleRate + "Hz. Pitch issues possible.");
			}
			writeVag(instr, outPath);
		}
		instr.close();	
	}
}
