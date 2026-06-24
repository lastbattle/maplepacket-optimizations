package game.packets;

/**
 * Provides MapleStory's custom encryption routines.
 *
 * @author Frz
 * @since Revision 211
 * @version 1.0
 */
public class MapleCustomEncryption {

    private static final int[][] ROLL_LEFT = createRollTable(true);
    private static final int[][] ROLL_RIGHT = createRollTable(false);
    private static final int[] ROLL_LEFT_3 = ROLL_LEFT[3];
    private static final int[] ROLL_LEFT_4 = ROLL_LEFT[4];
    private static final int[] ROLL_RIGHT_3 = ROLL_RIGHT[3];
    private static final int[] ROLL_RIGHT_4 = ROLL_RIGHT[4];

    private static int[][] createRollTable(boolean left) {
        int[][] table = new int[8][256];
        for (int shift = 0; shift < table.length; shift++) {
            for (int value = 0; value < table[shift].length; value++) {
                if (left) {
                    table[shift][value] = ((value << shift) | (value >>> (8 - shift))) & 0xFF;
                } else {
                    table[shift][value] = ((value >>> shift) | (value << (8 - shift))) & 0xFF;
                }
            }
        }
        return table;
    }

    /**
     * Encrypts <code>data</code> with Maple's encryption routines.
     *
     * @param data The data to encrypt.
     * @return The encrypted data.
     */
    public static final byte[] encryptData(final byte data[]) {
        final int length = data.length;
	for (int j = 0; j < 6; j++) {
	    int remember = 0;
	    int dataLength = length & 0xFF;
            
	    if (j % 2 == 0) {
		for (int i = 0; i < length; i++) {
		    int cur = data[i] & 0xFF;
		    cur = ROLL_LEFT_3[cur];
		    cur = (cur + dataLength) & 0xFF;
		    cur ^= remember;
		    remember = cur;
		    cur = ROLL_RIGHT[dataLength & 7][cur];
		    cur = (~cur) & 0xFF;
		    cur = (cur + 0x48) & 0xFF;
		    dataLength = (dataLength - 1) & 0xFF;
		    data[i] = (byte) cur;
		}
	    } else {
		for (int i = length - 1; i >= 0; i--) {
		    int cur = data[i] & 0xFF;
		    cur = ROLL_LEFT_4[cur];
		    cur = (cur + dataLength) & 0xFF;
		    cur ^= remember;
		    remember = cur;
		    cur ^= 0x13;
		    cur = ROLL_RIGHT_3[cur];
		    dataLength = (dataLength - 1) & 0xFF;
		    data[i] = (byte) cur;
		}
	    }
	    //System.out.println("enc after iteration " + j + ": " + HexTool.toString(data) + " al: " + al);
	}
	return data;
    }

    /**
     * Decrypts <code>data</code> with Maple's encryption routines.
     *
     * @param data The data to decrypt.
     * @return The decrypted data.
     */
    public static final byte[] decryptData(final byte data[]) {
        final int length = data.length;
	for (int j = 1; j <= 6; j++) {
	    int remember = 0;
	    int dataLength = length & 0xFF;
	    int nextRemember;

	    if (j % 2 == 0) {
		for (int i = 0; i < length; i++) {
		    int cur = data[i] & 0xFF;
		    cur = (cur - 0x48) & 0xFF;
		    cur = (~cur) & 0xFF;
		    cur = ROLL_LEFT[dataLength & 7][cur];
		    nextRemember = cur;
		    cur ^= remember;
		    remember = nextRemember;
		    cur = (cur - dataLength) & 0xFF;
		    cur = ROLL_RIGHT_3[cur];
		    data[i] = (byte) cur;
		    dataLength = (dataLength - 1) & 0xFF;
		}
	    } else {
		for (int i = length - 1; i >= 0; i--) {
		    int cur = data[i] & 0xFF;
		    cur = ROLL_LEFT_3[cur];
		    cur ^= 0x13;
		    nextRemember = cur;
		    cur ^= remember;
		    remember = nextRemember;
		    cur = (cur - dataLength) & 0xFF;
		    cur = ROLL_RIGHT_4[cur];
		    data[i] = (byte) cur;
		    dataLength = (dataLength - 1) & 0xFF;
		}
	    }
	    //System.out.println("dec after iteration " + j + ": " + HexTool.toString(data));
	}
	return data;
    }
}
