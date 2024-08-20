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
	
	public static FileBuffer genDummySecBaseJ_M2F1() {
		FileBuffer buff = new FileBuffer(ISO.SECSIZE, true);
		for(int i = 0; i < ISO.SYNC.length; i++) buff.addToFile(ISO.SYNC[i]);
		buff.addToFile(0x00000002); //Mode 2, leave coordinate blank for now.
		buff.addToFile(0x00000800); //Subheader
		buff.addToFile(0x00000800);
		
		long fsize = buff.getFileSize();
		while(fsize < 0x818) {
			for(int i = 0; i < 0x3f; i++) buff.addToFile((byte)0x30);
			buff.addToFile((byte)0x0a);
			fsize += 0x40; 
		}
		
		while(fsize < ISO.SECSIZE) {
			buff.addToFile(FileBuffer.ZERO_BYTE);
			fsize++;
		}
		
		return buff;
	}
	
	public static FileBuffer genDummySecBaseI_M2F1() {
		FileBuffer buff = new FileBuffer(ISO.SECSIZE, true);
		for(int i = 0; i < ISO.SYNC.length; i++) buff.addToFile(ISO.SYNC[i]);
		buff.addToFile(0x00000002); //Mode 2, leave coordinate blank for now.
		buff.addToFile(0x00000800); //Subheader
		buff.addToFile(0x00000800);
		
		long fsize = buff.getFileSize();
		while(fsize < ISO.SECSIZE) {
			buff.addToFile(FileBuffer.ZERO_BYTE);
			fsize++;
		}
		
		return buff;
	}
	
	public static FileBuffer genDummySecBase_M2F2() {
		FileBuffer buff = new FileBuffer(ISO.SECSIZE, true);
		for(int i = 0; i < ISO.SYNC.length; i++) buff.addToFile(ISO.SYNC[i]);
		buff.addToFile(0x00000002); //Mode 2, leave coordinate blank for now.
		buff.addToFile(0x00002000); //Subheader
		buff.addToFile(0x00002000);
		
		long fsize = buff.getFileSize();
		while(fsize < ISO.SECSIZE) {
			buff.addToFile(FileBuffer.ZERO_BYTE);
			fsize++;
		}
		
		return buff;
	}
	
	public static int writeDummySecJToCDStream(OutputStream out, int absSec) throws IOException {
		if(out == null) return 0;
		FileBuffer dummy = genDummySecBaseJ_M2F1();
		dummy.replaceByte(ISO.getBCDminute(absSec), 0xc);
		dummy.replaceByte(ISO.getBCDsecond(absSec), 0xd);
		dummy.replaceByte(ISO.getBCDsector(absSec), 0xe);
		ISOUtils.updateSectorChecksumsM2F1(dummy);
		
		dummy.writeToStream(out);
		return 1;
	}
	
	public static int writeDataFileToCDStream_M2F1(OutputStream out, FileBuffer data, int absSec, byte fillByte, boolean flagEnd) throws IOException {
		//Return number of sectors written
		if(out == null) return 0;
		if(data == null) return 0;
		
		int secWritten = 0;
		FileBuffer buff = new FileBuffer(ISO.SECSIZE, true);
		
		//All this stuff at the top except the sector number info will be the same for every sector
		for(int i = 0; i < ISO.SYNC.length; i++) buff.addToFile(ISO.SYNC[i]);
		buff.addToFile(ISO.getBCDminute(absSec));
		buff.addToFile(ISO.getBCDsecond(absSec));
		buff.addToFile(ISO.getBCDsector(absSec));
		buff.addToFile((byte)2); //Mode 2
		buff.addToFile(0x00000800); //Subheader. Changes only for last sector.
		buff.addToFile(0x00000800);
		
		//Fill
		for(int i = 0x18; i < ISO.SECSIZE; i++) buff.addToFile(FileBuffer.ZERO_BYTE);
		
		long fsize = data.getFileSize();
		int secCount = (int)(fsize + 0x7ff) >>> 11;
		int s = 0;
		int spos = 0;
		long cpos = 0;
		for(s = 0; s < secCount; s++) {
			if(s != 0) {
				//Update sector info
				updateSectorNumber(buff, absSec);
			}
			absSec++;
			
			if(flagEnd && (s == (secCount - 1))) {
				//Last sector
				buff.replaceByte((byte)0x89, 0x12);
				buff.replaceByte((byte)0x89, 0x16);
			}
			spos = 0x18;
			
			for(int j = 0; j < 0x800; j++) {
				if(cpos < fsize) buff.replaceByte(data.getByte(cpos++), spos++);
				else buff.replaceByte(fillByte, spos++);
			}
			
			//Checksums
			if(!ISOUtils.updateSectorChecksumsM2F1(buff)) {
				MyuPackagerLogger.logMessage("MyuCD.writeDataFileToCDStream_M2F1", "Sector checksum update failed!");
				return secWritten;
			}
			
			buff.writeToStream(out);
			secWritten++;
		}
		
		return secWritten;
	}

	public static int copyXAStreamToCD(OutputStream out, InputStream in, int startAbsSec, boolean form2) throws IOException {
		if(out == null) return 0;
		if(in == null) return 0;
		
		int startSec = startAbsSec;
		int nowSec = startSec;
		
		FileBuffer buffer = new FileBuffer(ISO.SECSIZE, false);
		for(int i = 0; i < ISO.SECSIZE; i++) buffer.addToFile(FileBuffer.ZERO_BYTE);
		
		boolean eof = false;
		while(!eof) {
			//Copy in new data
			for(int i = 0; i < ISO.SECSIZE; i++) {
				if(!eof) {
					int b = in.read();
					if(b != -1) {
						buffer.replaceByte((byte)b, i);
					}
					else {
						eof = true;
						buffer.replaceByte((byte)0x00, i);
					}
				}
				else buffer.replaceByte((byte)0x00, i);
			}
			
			//Update sector time
			updateSectorNumber(buffer, nowSec);
			
			//Update checksums
			if(form2) ISOUtils.updateSectorChecksumsM2F2(buffer);
			else ISOUtils.updateSectorChecksumsM2F1(buffer);
			
			//Write
			buffer.writeToStream(out);
			
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
		
		return (min * 60 * 75) + (scd * 60) + sct;
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
		cdt.setFrame(Integer.parseInt(tz));
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
