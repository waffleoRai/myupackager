package waffleoRai_extractMyu.test;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_extractMyu.MyuArcCommon;

public class Test_GarbageStr2File {

	public static void main(String[] args) {
		String outputPath = "D:\\usr\\bghos\\code\\psx_stuff\\tmmpsx-build-test\\assets\\arcspec\\match\\EFFECT_f000.bin";
		String datastr = "N20;Z3;N0a;Z12;N60;Z6;N20;Z3;N12;Z7;N122b1f6688ed14;Z1;N69;Z1;N66;Z1;N70;Z7;Nb8d25d2c09ddc101c6cfb6e9682ec2018039c796f6cec1018039c796f6cec1013623;Z7;N30;Z6;N20;Z3;N0c;Z8;N2cc201d8ed14;Z6;N50;Z6;N20;Z3;N18;Z1;N266e";
		
		try {
			FileBuffer buff = MyuArcCommon.bufferGarbageString2Data(datastr);
			buff.writeFile(outputPath);
		}
		catch(Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		
		
	}
	
}
