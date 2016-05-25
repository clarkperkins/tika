/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.dbf;

import org.apache.commons.io.IOUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.EndianUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

class DBFFileHeader {

    private DBFReader.Version version;
    private Calendar lastModified;
    private int numRecords = -1;
    private short numBytesInHeader;
    private short numBytesInRecord;
    private DBFColumnHeader[] cols;

    public static DBFFileHeader parse(InputStream is) throws IOException, TikaException {
        DBFFileHeader header = new DBFFileHeader();

        int firstByte = is.read();
        header.version = DBFReader.getVersion(firstByte);
        if (header.version == null) {
            throw new TikaException("Unrecognized first byte in DBFFile: " + firstByte);
        }
        int lastModYear = is.read();
        int lastModMonth = is.read();
        int lastModDay = is.read();
        Calendar now = GregorianCalendar.getInstance(
                TimeZone.getTimeZone("UTC"), Locale.ROOT);

        //if this was last modified after the current year, assume
        //the file was created in 1900
        if (lastModYear + 2000 > now.get(Calendar.YEAR)) {
            lastModYear += 1900;
        } else {
            lastModYear += 2000;
        }
        Calendar lastModified = new GregorianCalendar(
                TimeZone.getTimeZone("UTC"), Locale.ROOT);
        lastModified.set(lastModYear, lastModMonth - 1, lastModDay,0,0,0);
        header.lastModified = lastModified;

        header.numRecords = EndianUtils.readIntLE(is);
        header.numBytesInHeader = EndianUtils.readShortLE(is);
        header.numBytesInRecord = EndianUtils.readShortLE(is);
        IOUtils.skipFully(is, 20);//TODO: can get useful info out of here

        int numCols = (header.numBytesInHeader - 32) / 32;

        header.cols = new DBFColumnHeader[numCols];
        for (int i = 0; i < numCols; i++) {
            header.cols[i] = readCol(is);
        }
        int endOfHeader = is.read();
        if (endOfHeader != 13) {
            throw new TikaException("Expected new line at end of header");
        }
        long totalReadSoFar = 32 + (numCols * 32) + 1;
        //there can be extra bytes in the header
        long extraHeaderBytes = header.numBytesInHeader - totalReadSoFar;
        IOUtils.skipFully(is, extraHeaderBytes);
        return header;
    }

    private static DBFColumnHeader readCol(InputStream is) throws IOException, TikaException {
        byte[] headerName = new byte[11];
        IOUtils.readFully(is, headerName);
        DBFColumnHeader col = new DBFColumnHeader();
        headerName = DBFReader.trim(headerName);
        col.name = new String(headerName, StandardCharsets.US_ASCII);
        int colType = is.read();
        if (colType < 0) {
            throw new IOException("File truncated before coltype in header");
        }
        col.setType(colType);
        IOUtils.skipFully(is, 4);//field data address
        col.fieldLength = is.read();
        if (col.fieldLength < 0) {
            throw new TikaException("Field length for column "+headerName+"is < 0");
        } else if (col.fieldLength > DBFReader.MAX_FIELD_LENGTH) {
            throw new TikaException("Field length ("+col.fieldLength+") is greater than DBReader.MAX_FIELD_LENGTH ("+
                    DBFReader.MAX_FIELD_LENGTH+")");
        }
        col.decimalCount = is.read();
        IOUtils.skipFully(is, 14); //TODO: might have useful info in some versions
        return col;
    }

    DBFColumnHeader[] getCols() {
        return cols;
    }

    int getNumRecords() {
        return numRecords;
    }

    Calendar getLastModified() {
        return lastModified;
    }

    DBFReader.Version getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "DBFFileHeader{" +
                "lastModified=" + lastModified +
                ", numRecords=" + numRecords +
                ", numBytesInHeader=" + numBytesInHeader +
                ", numBytesInRecord=" + numBytesInRecord +
                ", cols=" + Arrays.toString(cols) +
                '}';
    }
}
