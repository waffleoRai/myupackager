package waffleoRai_extractMyu;

public class MyuCode {
	
	
	public static final int MIPS_RELOC_32 = 2;
	public static final int MIPS_RELOC_26 = 4;
	public static final int MIPS_RELOC_HI16 = 5;
	public static final int MIPS_RELOC_LO16 = 6;
	public static final int MIPS_RELOC_GPREL16 = 7;
	public static final int MIPS_RELOC_PC16 = 10;
	
	public static long exeOffset2Address(int offset) {
		long addr = Integer.toUnsignedLong(offset);
		addr -= 0x800;
		addr += 0x10000;
		addr |= 0x80000000;
		return addr;
	}
	
	public static int address2ExeOffset(long address) {
		address &= 0x7fffffff;
		address -= 0x10000;
		address += 0x800;
		return (int)address;
	}

}
