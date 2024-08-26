package waffleoRai_extractMyu;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import waffleoRai_Containers.CDDateTime;
import waffleoRai_Containers.ISO;
import waffleoRai_Containers.ISOUtils;
import waffleoRai_Containers.ISOXAImage;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.MultiFileBuffer;

public class MyuCD {
	
	public static final int SEC_OFS_SMINUTE = 0xc;
	public static final int SEC_OFS_SSECOND = 0xd;
	public static final int SEC_OFS_SSECTOR = 0xe;
	
	public static void extractPSLogo(ISOXAImage cd_image, String writePath) throws IOException {
		FileBuffer pslogo = new MultiFileBuffer(7);
		for(int i = 0; i < 7; i++) {
			FileBuffer dat = cd_image.getSectorData(5+i);
			if(i == 6) {
				dat = dat.createReadOnlyCopy(0, 0x290 - 0x18);
			}
			pslogo.addToFile(dat);
		}
		pslogo.writeFile(writePath);
		pslogo.dispose();
	}
	
	public static byte[] genDummySecBaseJ_M2F1() {
		byte[] sec = new byte[ISO.SECSIZE];
		for(int i = 0; i < ISO.SYNC.length; i++) sec[i] = ISO.SYNC[i];
		sec[0xf] = 2;
		sec[0x12] = 8;
		sec[0x16] = 8;
		
		int cpos = 0x18;
		while(cpos < 0x818) {
			for(int i = 0; i < 0x3f; i++) sec[cpos++] = 0x30;
			sec[cpos++] = 0x0a;
		}
		
		//Defaults to zero for everything else.
		return sec;
	}
	
	public static byte[] genDummySecBaseI_M2F1() {
		byte[] sec = new byte[ISO.SECSIZE];
		for(int i = 0; i < ISO.SYNC.length; i++) sec[i] = ISO.SYNC[i];
		sec[0xf] = 2;
		sec[0x12] = 8;
		sec[0x16] = 8;

		return sec;
	}
	
	public static byte[] genDummySecBase_M2F2() {
		byte[] sec = new byte[ISO.SECSIZE];
		for(int i = 0; i < ISO.SYNC.length; i++) sec[i] = ISO.SYNC[i];
		sec[0xf] = 2;
		sec[0x12] = 0x20;
		sec[0x16] = 0x20;

		return sec;
	}
	
	public static void resetSectorBufferM2F1I(byte[] sec) {
		for(int i = 0xc; i < ISO.SECSIZE; i++) sec[i] = 0;
		sec[0xf] = 2;
		sec[0x12] = 8;
		sec[0x16] = 8;
	}
	
	public static void resetSectorBufferM2F1I(FileBuffer sec) {
		for(int i = 0xc; i < ISO.SECSIZE; i++) sec.replaceByte((byte)0, i);
		sec.replaceByte((byte)2, 0xfL);
		sec.replaceByte((byte)8, 0x12L);
		sec.replaceByte((byte)8, 0x16L);
	}
	
	public static int writeDummySecsJToCDStream(OutputStream out, int startSecAbs, int count) throws IOException {
		if(out == null) return 0;
		byte[] dummy = genDummySecBaseJ_M2F1();

		int written = 0;
		for(int i = 0; i < count; i++) {
			updateSectorNumber(dummy, startSecAbs+i);
			ISOUtils.updateSectorChecksumsM2F1(dummy);
			out.write(dummy);
			written++;
		}
	
		return written;
	}
	
	public static int writeDummySecsIToCDStream(OutputStream out, int startSecAbs, int count) throws IOException {
		if(out == null) return 0;
		byte[] dummy = genDummySecBaseI_M2F1();
		
		int written = 0;
		for(int i = 0; i < count; i++) {
			updateSectorNumber(dummy, startSecAbs+i);
			ISOUtils.updateSectorChecksumsM2F1(dummy);
			out.write(dummy);
			written++;
		}
	
		return written;
	}
	
	public static int writeDummySecsF2ToCDStream(OutputStream out, int startSecAbs, int count) throws IOException {
		if(out == null) return 0;
		byte[] dummy = genDummySecBase_M2F2();
		
		int written = 0;
		for(int i = 0; i < count; i++) {
			updateSectorNumber(dummy, startSecAbs+i);
			ISOUtils.updateSectorChecksumsM2F2(dummy);
			out.write(dummy);
			written++;
		}
	
		return written;
	}
	
	public static int writeDataFileToCDStream_M2F1(OutputStream out, FileBuffer data, int absSec, byte fillByte, boolean flagEnd) throws IOException {
		//Return number of sectors written
		if(out == null) return 0;
		if(data == null) return 0;
		
		int secWritten = 0;
		byte[] buff = genDummySecBaseI_M2F1();

		long fsize = data.getFileSize();
		int secCount = (int)(fsize + 0x7ff) >>> 11;
		int s = 0;
		int spos = 0;
		long cpos = 0;
		for(s = 0; s < secCount; s++) {
			updateSectorNumber(buff, absSec+s);
			
			if(flagEnd && (s == (secCount - 1))) {
				//Last sector
				buff[0x12] = (byte)0x89;
				buff[0x16] = (byte)0x89;
			}
			spos = 0x18;
			
			for(int j = 0; j < 0x800; j++) {
				if(cpos < fsize) buff[spos++] = data.getByte(cpos++);
				else buff[spos++]= fillByte;
			}
			
			//Checksums
			if(!ISOUtils.updateSectorChecksumsM2F1(buff)) {
				MyuPackagerLogger.logMessage("MyuCD.writeDataFileToCDStream_M2F1", "Sector checksum update failed!");
				return secWritten;
			}
			
			out.write(buff);
			secWritten++;
		}
		
		return secWritten;
	}

	public static int copyXAStreamToCD(OutputStream out, InputStream in, int startAbsSec) throws IOException {
		if(out == null) return 0;
		if(in == null) return 0;
		
		int startSec = startAbsSec;
		int nowSec = startSec;
		
		byte[] buffer = genDummySecBase_M2F2();
		
		boolean eof = false;
		while(!eof) {
			//Copy in new data
			boolean eofzero = false;
			for(int i = 0; i < ISO.SECSIZE; i++) {
				if(!eof) {
					int b = in.read();
					if(b != -1) {
						buffer[i] = (byte)b;
					}
					else {
						eof = true;
						buffer[i] = 0;
						if(i == 0) {
							eofzero = true;
							break;
						}
					}
				}
				else buffer[i] = 0;
			}
			if(eofzero) {
				//This sector is not used.
				break;
			}
			
			updateSectorNumber(buffer, nowSec);
			
			//Instead of asking if form2, check if THIS sector is form2
			int submode = Byte.toUnsignedInt(buffer[0x12]);
			boolean form2 = ((submode & 0x20) != 0);
			
			//Update checksums
			if(form2) ISOUtils.updateSectorChecksumsM2F2(buffer);
			else ISOUtils.updateSectorChecksumsM2F1(buffer);
			
			//Write
			out.write(buffer);
			nowSec++;
		}
		
		return nowSec - startSec;
	}
	
	public static String getSec4String_RegionJ() {
		StringBuilder sb = new StringBuilder(0x40);
		for(int i = 0; i < 10; i++) sb.append(' ');
		sb.append("Licensed  by");
		for(int i = 0; i < 10; i++) sb.append(' ');
		sb.append("Sony Computer Entertainment Inc.\n");
		return sb.toString();
	}
	
	public static String getSec4String_RegionE() {
		StringBuilder sb = new StringBuilder(0x40);
		for(int i = 0; i < 10; i++) sb.append(' ');
		sb.append("Licensed  by");
		for(int i = 0; i < 10; i++) sb.append(' ');
		sb.append("Sony Computer Entertainment Euro pe   ");
		return sb.toString();
	}
	
	public static String getSec4String_RegionU() {
		StringBuilder sb = new StringBuilder(0x40);
		for(int i = 0; i < 10; i++) sb.append(' ');
		sb.append("Licensed  by");
		for(int i = 0; i < 10; i++) sb.append(' ');
		sb.append("Sony Computer Entertainment Amer  ica ");
		return sb.toString();
	}
	
	public static void updateSectorNumber(FileBuffer sectordat, int absSec) {
		if(sectordat == null) return;
		sectordat.replaceByte(ISO.getBCDminute(absSec), 0xcL);
		sectordat.replaceByte(ISO.getBCDsecond(absSec), 0xdL);
		sectordat.replaceByte(ISO.getBCDsector(absSec), 0xeL);
	}
	
	public static void updateSectorNumber(byte[] sectordat, int absSec) {
		if(sectordat == null) return;
		sectordat[SEC_OFS_SMINUTE] = ISO.getBCDminute(absSec);
		sectordat[SEC_OFS_SSECOND] = ISO.getBCDsecond(absSec);
		sectordat[SEC_OFS_SSECTOR] = ISO.getBCDsector(absSec);
	}
	
	public static int getSectorMinuteRaw(int absSec) {
		return (absSec / (60*75));
	}
	
	public static int getSectorSecondRaw(int absSec) {
		int min = getSectorMinuteRaw(absSec);
		absSec -= (min*60);
		
		return (absSec / 75);
	}
	
	public static int getSectorFrameRaw(int absSec) {
		return absSec % 75;
	}
	
	public static String sectorNumberToTimeString(int sec) {
		int m = getSectorMinuteRaw(sec);
		int s = getSectorSecondRaw(sec);
		int f = getSectorFrameRaw(sec);
		return String.format("%02d:%02d:%02d", m,s,f);
	}
	
	public static int readSectorTimeString(String xmlstr) {
		if(xmlstr == null) return 0;
		String[] spl = xmlstr.split(":");
		int min = 0;
		int scd = 0;
		int sct = 0;
		
		if(spl.length > 2) {
			min = Integer.parseInt(spl[0]);
			scd = Integer.parseInt(spl[1]);
			sct = Integer.parseInt(spl[2]);
		}
		else if(spl.length > 1) {
			scd = Integer.parseInt(spl[0]);
			sct = Integer.parseInt(spl[1]);
		}
		else {
			sct = Integer.parseInt(spl[0]);
		}
		
		return (min * 60 * 75) + (scd * 75) + sct;
	}
	
	public static CDDateTime readXMLTimestamp(String value) {
		CDDateTime cdt = new CDDateTime();
		if(value == null) return cdt;
		String[] spl = value.split(" ");
		
		String[] spl1 = spl[0].split("/");
		cdt.setYear(Integer.parseInt(spl1[0]));
		cdt.setMonth(Integer.parseInt(spl1[1]));
		cdt.setDay(Integer.parseInt(spl1[2]));
		
		spl1 = spl[1].split(":");
		cdt.setHour(Integer.parseInt(spl1[0]));
		cdt.setMinute(Integer.parseInt(spl1[1]));
		cdt.setSecond(Integer.parseInt(spl1[2]));
		cdt.setFrame(Integer.parseInt(spl1[3]));
		
		String tz = spl[2].replace("+", "");
		cdt.setTimezone(Integer.parseInt(tz));
		return cdt;
	}
	
	public static CDDateTime convertFileTime(FileTime time) {
		CDDateTime cdt = new CDDateTime();
		if(time == null) return cdt;
		
		ZonedDateTime zdt = ZonedDateTime.ofInstant(time.toInstant(), ZoneId.systemDefault());
		cdt.setYear(zdt.getYear());
		cdt.setMonth(zdt.getMonthValue());
		cdt.setDay(zdt.getDayOfMonth());
		cdt.setHour(zdt.getHour());
		cdt.setMinute(zdt.getMinute());
		cdt.setSecond(zdt.getSecond());
		cdt.setFrame(0);
		cdt.setTimezone(zdt.getOffset().getTotalSeconds()/3600); //?I'm not actually sure how the zones are specified lol
		
		return cdt;
	}
	
	public static byte[] serializeDateTimeBin(CDDateTime time) {
		if(time == null) return null;
		byte[] dat = new byte[7];
		
		dat[0] = (byte)(time.getYear() - 1900);
		dat[1] = (byte)(time.getMonth());
		dat[2] = (byte)(time.getDay());
		dat[3] = (byte)(time.getHour());
		dat[4] = (byte)(time.getMinute());
		dat[5] = (byte)(time.getSecond());
		dat[6] = (byte)(time.getTimezone());
		
		return dat;
	}
	
	
}
