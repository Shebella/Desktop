package org.itri.ccma.safebox.s3;

import java.math.BigDecimal;

public class S3BucketSize {
	static private final int ONE_GIGA_IN_BYTE = 1073741824;
	private boolean validate = false;
	private BigDecimal maxBytes = null;
	private BigDecimal usedBytes = null;
	private boolean isExceed = false;
	private int objectCount = -1;

	public boolean isValidate() {
		return validate;
	}

	public void setValidate(boolean validate) {
		this.validate = validate;

		if (!validate) {
			objectCount = -1;
		}
	}

	public BigDecimal getMaxBytes() {
		return maxBytes;
	}

	public void setMaxBytes(BigDecimal maxBytes) {
		this.maxBytes = maxBytes;
	}

	public BigDecimal getUsedBytes() {
		return usedBytes;
	}

	public void setUsedBytes(BigDecimal usedBytes) {
		this.usedBytes = usedBytes;

		isExceed = false;
		if (null != maxBytes && -1 < usedBytes.compareTo(maxBytes)) {
			isExceed = true;
		}
	}

	public boolean isExceed() {
		return isExceed;
	}

	public S3BucketSize() {
		validate = false;
		maxBytes = new BigDecimal("21474836480");// 20GB
		usedBytes = BigDecimal.ZERO;
	}

	public void Add(int fileSize) {
		if (null == usedBytes) {
			usedBytes = new BigDecimal(0);
		}

		usedBytes = usedBytes.add(new BigDecimal(fileSize));
	}

	public BigDecimal GetRemainSize() {

		return maxBytes.subtract(usedBytes);
	}

	public String GetMaxSizeString() {
		String s = "";
		s += maxBytes.divide(new BigDecimal(ONE_GIGA_IN_BYTE));
		s += "G";

		return s;
	}

	public String GetUsedSizeString() {

		return usedBytes.toString();
	}

	public String GetUsedRateString() {
		String s = "";
		BigDecimal zero;
		BigDecimal one;
		BigDecimal hundred;
		BigDecimal rate;

		if (usedBytes == null || maxBytes == null) {
			return "20 GB user space";
		}

		zero = new BigDecimal(0);
		one = new BigDecimal(1);
		hundred = new BigDecimal(100);
		try {
			rate = usedBytes.multiply(hundred).divide(maxBytes, 4, BigDecimal.ROUND_HALF_EVEN);

			if (rate.compareTo(one) < 0 && usedBytes.compareTo(zero) > 0) {
				s = "1";
			} else if (rate.compareTo(hundred) > 0) {
				s = "100";
			} else {
				s = rate.setScale(2, BigDecimal.ROUND_HALF_UP).toString();
			}
		} catch (Exception e) {
			s = "0";
		}

		s += "% of ";
		s += maxBytes.divide(new BigDecimal(ONE_GIGA_IN_BYTE));
		s += "GB used";

		if (isExceed) {
			s = "Limit exceed:  " + s;
		}

		return s;
	}

	public int getObjectCount() {
		return objectCount;
	}

	public void setObjectCount(int objectCount) {
		this.objectCount = objectCount;
	}
}