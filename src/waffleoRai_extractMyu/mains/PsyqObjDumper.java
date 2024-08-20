package waffleoRai_extractMyu.mains;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_extractMyu.MyuPackagerLogger;
import waffleoRai_extractMyu.psyq.PsyqItem;
import waffleoRai_extractMyu.psyq.PsyqLib;
import waffleoRai_extractMyu.psyq.PsyqObj;
import waffleoRai_extractMyu.psyq.PsyqSection;

public class PsyqObjDumper {
	
	private String inPath;
	private String outPath;
	
	private static void printSectionInfo(PsyqObj obj) {
		System.out.println("---> " + obj.getName());
		List<PsyqSection> secs = obj.getAllSections();
		for(PsyqSection sec : secs) {
			int size = sec.getSize();
			List<PsyqItem> syms = sec.getSymbols();
			if(size > 0 || !syms.isEmpty()) {
				System.out.print(String.format("0x%04x\t", sec.getID()));
				System.out.print(sec.getName() + "\t");
				System.out.print(String.format("0x%x\n", sec.getSize()));
			}
		}
	}
	
	private static boolean checkArgs(Map<String, String> argmap, PsyqObjDumper ctx) throws IOException {
		ctx.inPath = argmap.get("input");
		ctx.outPath = argmap.get("xmlout");
		
		if(ctx.inPath == null) {
			MyuPackagerLogger.logMessage("PsyqObjDumper.checkArgs", "Input path is required!");
			return false;
		}
		
		if(!FileBuffer.fileExists(ctx.inPath)) {
			MyuPackagerLogger.logMessage("PsyqObjDumper.checkArgs", "Provided input file \"" + ctx.inPath + "\" does not exist!");
			return false;
		}
		
		if(ctx.outPath == null) {
			//Derive from input
			String indir = ctx.inPath.substring(0, ctx.inPath.lastIndexOf(File.separator));
			if(ctx.inPath.toUpperCase().endsWith(".OBJ")) {
				String instem = ctx.inPath.substring(0, ctx.inPath.lastIndexOf(".OBJ"));
				ctx.outPath = instem + ".xml";
			}
			else {
				ctx.outPath = indir;
			}
			
			MyuPackagerLogger.logMessage("PsyqObjDumper.checkArgs", "Output path not provided. Set to: " + ctx.outPath);
		}
		
		if(ctx.inPath.toUpperCase().endsWith(".LIB")) {
			if(!FileBuffer.directoryExists(ctx.outPath)) {
				Files.createDirectories(Paths.get(ctx.outPath));
			}
		}
		
		return true;
	}
	
	public static void main_obj2xml(Map<String, String> argmap) throws IOException, UnsupportedFileTypeException {
		PsyqObjDumper ctx = new PsyqObjDumper();
		
		if(!checkArgs(argmap, ctx)) {
			MyuPackagerLogger.logMessage("PsyqObjDumper.main_obj2xml", "Arg check failed! Exiting...");
			System.exit(1);
		}
		
		String inLower = ctx.inPath.toLowerCase();
		if(inLower.endsWith(".lib")) {
			FileBuffer buff = FileBuffer.createBuffer(ctx.inPath, false);
			PsyqLib lib = PsyqLib.parse(buff.getReferenceAt(0L));
			
			List<PsyqObj> objList = lib.getContents();
			for(PsyqObj obj : objList) {
				String xmlout = ctx.outPath + File.separator + obj.getName() + ".xml";
				BufferedWriter bw = new BufferedWriter(new FileWriter(xmlout));
				bw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
				obj.writeToXML(bw, "");
				bw.close();
				
				printSectionInfo(obj);
			}
		}
		else if(inLower.endsWith(".obj")) {
			FileBuffer buff = FileBuffer.createBuffer(ctx.inPath, false);
			PsyqObj obj = PsyqObj.parse(buff.getReferenceAt(0L));
			BufferedWriter bw = new BufferedWriter(new FileWriter(ctx.outPath));
			bw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			obj.writeToXML(bw, "");
			bw.close();
			printSectionInfo(obj);
		}
		else {
			MyuPackagerLogger.logMessage("PsyqObjDumper.main_obj2xml", "Input file name extension not recognized. Exiting...");
			System.exit(1);
		}
		
	}

}
