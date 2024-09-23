package waffleoRai_extractMyu.mains;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import waffleoRai_extractMyu.MyuArcCommon;

public class Rmjrra {
	
	public static void main_rmjrra(Map<String, String> argmap) throws IOException {
		String inpath = MyuArcCommon.getSystemAbsolutePath(argmap.get("input"));
		String outpath = MyuArcCommon.getSystemAbsolutePath(argmap.get("output"));
		
		if(outpath == null) outpath = inpath + ".tmp";
		
		BufferedReader br = new BufferedReader(new FileReader(inpath));
		BufferedWriter bw = new BufferedWriter(new FileWriter(outpath));
		String line = null;
		String lastline = null;
		while((line = br.readLine()) != null) {
			if(!line.contains(".end") || !line.contains("__nojrra_")) {
				if(lastline != null) bw.write(lastline + "\n");
			}
			else {
				if(!lastline.contains("j") || !lastline.contains("$31")) {
					if(lastline != null) bw.write(lastline + "\n");
				}
			}
			lastline = line;
		}
		if(lastline != null) bw.write(lastline + "\n");
		bw.close();
		br.close();
		
	}

}
