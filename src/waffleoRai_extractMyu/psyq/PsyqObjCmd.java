package waffleoRai_extractMyu.psyq;

import java.util.HashMap;
import java.util.Map;

public class PsyqObjCmd {
	
	//https://github.com/grumpycoders/pcsx-redux/blob/main/tools/psyq-obj-parser/psyq-obj-parser.cc
	
	/*----------------- OBJ Commands -----------------*/
	
	public static final int END = 0x00;
	public static final int BYTES = 0x02;
	public static final int SWITCH = 0x06;
	public static final int ZEROES = 0x08;
	public static final int RELOC = 0x0a;
	public static final int EXPORT = 0x0c;
	public static final int IMPORT = 0x0e;
	public static final int SECTION = 0x10;
	public static final int LOCAL_SYM = 0x12;
	public static final int FILE_NAME = 0x1c;
	public static final int PROG_TYPE = 0x2e;
	public static final int UNINIT = 0x30;
	public static final int INC_SLD_LINENUM = 0x32;
	public static final int INC_SLD_LINENUM_BY_BYTE = 0x34;
	public static final int INC_SLD_LINENUM_BY_WORD = 0x36;
	public static final int SET_SLD_LINENUM = 0x38;
	public static final int SET_SLD_LINENUM_FILE = 0x3a;
	public static final int END_SLD = 0x3c;
	public static final int FUNCTION = 0x4a;
	public static final int FUNCTION_END = 0x4c;
	public static final int BLOCK_START = 0x4e;
	public static final int BLOCK_END = 0x50;
	public static final int SEC_DEF = 0x52;
	public static final int SEC_DEF2 = 0x54;
	public static final int FUNC_START_2 = 0x56;
	
	private static int[] sCmdInts = {END, BYTES, SWITCH, ZEROES, RELOC, EXPORT, IMPORT, SECTION,
							  LOCAL_SYM, FILE_NAME, PROG_TYPE, UNINIT, 
							  INC_SLD_LINENUM, INC_SLD_LINENUM_BY_BYTE, INC_SLD_LINENUM_BY_WORD, SET_SLD_LINENUM,
							  SET_SLD_LINENUM_FILE, END_SLD, FUNCTION, FUNCTION_END,
							  BLOCK_START, BLOCK_END, SEC_DEF, SEC_DEF2, FUNC_START_2};
	
	private static String[] sCmdStr = {"END", "BYTES", "SWITCH", "ZEROES", "RELOC", "EXPORT", "IMPORT", "SECTION",
			  "LOCAL_SYM", "FILE_NAME", "PROG_TYPE", "UNINIT", 
			  "INC_SLD_LINENUM", "INC_SLD_LINENUM_BY_BYTE", "INC_SLD_LINENUM_BY_WORD", "SET_SLD_LINENUM",
			  "SET_SLD_LINENUM_FILE", "END_SLD", "FUNCTION", "FUNCTION_END",
			  "BLOCK_START", "BLOCK_END", "SEC_DEF", "SEC_DEF2", "FUNC_START_2"};
	
	private static Map<Integer, String> sCmdI2S;
	private static Map<String, Integer> sCmdS2I;
	
	public static String cmd2String(int cmd) {
		if(sCmdI2S == null) {
			sCmdI2S = new HashMap<Integer, String>();
			for(int i = 0; i < sCmdInts.length; i++) {
				sCmdI2S.put(sCmdInts[i], sCmdStr[i]);
			}
		}
		return sCmdI2S.get(cmd);
	}
	
	public static int string2Cmd(String cmd) {
		if(sCmdS2I == null) {
			sCmdS2I = new HashMap<String, Integer>();
			for(int i = 0; i < sCmdInts.length; i++) {
				sCmdS2I.put(sCmdStr[i], sCmdInts[i]);
			}
		}
		return sCmdS2I.get(cmd);
	}
	
	/*----------------- Expression Commands -----------------*/
	
	public static final int EXPR_VALUE = 0x00;
	public static final int EXPR_SYMBOL = 0x02;
	public static final int EXPR_SECBASE = 0x04;
	public static final int EXPR_SECSTART = 0x0c;
	public static final int EXPR_SECEND = 0x16;
	public static final int EXPR_ADD = 0x2c;
	public static final int EXPR_SUB = 0x2e;
	public static final int EXPR_DIV = 0x32;
	
	private static int[] sExpInts = {EXPR_VALUE, EXPR_SYMBOL, EXPR_SECBASE, EXPR_SECSTART,
									 EXPR_SECEND, EXPR_ADD, EXPR_SUB, EXPR_DIV};

	private static String[] sExpStr = {"VALUE", "SYMBOL", "SECBASE", "SECSTART", "SECEND", "ADD", "SUB", "DIV"};
	
	private static Map<Integer, String> sExprI2S;
	private static Map<String, Integer> sExprS2I;
	
	public static String expr2String(int exprCmd) {
		if(sExprI2S == null) {
			sExprI2S = new HashMap<Integer, String>();
			for(int i = 0; i < sExpInts.length; i++) {
				sExprI2S.put(sExpInts[i], sExpStr[i]);
			}
		}
		return sExprI2S.get(exprCmd);
	}
	
	public static int string2Expr(String exprCmd) {
		if(sExprS2I == null) {
			sExprS2I = new HashMap<String, Integer>();
			for(int i = 0; i < sExpInts.length; i++) {
				sExprS2I.put(sExpStr[i], sExpInts[i]);
			}
		}
		return sExprS2I.get(exprCmd);
	}
	
	/*----------------- Reloc Commands -----------------*/
	
	public static final int REL32_BE = 0x08;
	public static final int REL32 = 0x10;
	public static final int REL26 = 0x4a;
	public static final int HI16 = 0x52;
	public static final int LO16 = 0x54;
	public static final int REL26_BE = 0x5c;
	public static final int HI16_BE = 0x60;
	public static final int LO16_BE = 0x62;
	public static final int GPREL16 = 0x64;
	
	private static int[] sRelInts = {REL32_BE, REL32, REL26, HI16, LO16, REL26_BE, HI16_BE, LO16_BE, GPREL16};
	private static String[] sRelStr = {"REL32_BE", "REL32", "REL26", "HI16", "LO16", "REL26_BE", "HI16_BE", "LO16_BE", "GPREL16"};

	private static Map<Integer, String> sRelI2S;
	private static Map<String, Integer> sRelS2I;
	
	public static String reloc2String(int relCmd) {
		if(sRelI2S == null) {
			sRelI2S = new HashMap<Integer, String>();
			for(int i = 0; i < sRelInts.length; i++) {
				sRelI2S.put(sRelInts[i], sRelStr[i]);
			}
		}
		return sRelI2S.get(relCmd);
	}
	
	public static int string2Reloc(String relCmd) {
		if(sRelS2I == null) {
			sRelS2I = new HashMap<String, Integer>();
			for(int i = 0; i < sRelInts.length; i++) {
				sRelS2I.put(sRelStr[i], sRelInts[i]);
			}
		}
		return sRelS2I.get(relCmd);
	}
	
}
