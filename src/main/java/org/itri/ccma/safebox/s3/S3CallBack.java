package org.itri.ccma.safebox.s3;

import org.itri.ccma.safebox.db.ObjectException;
import org.jets3t.service.model.S3Object;

public interface S3CallBack {
	int cb_count = 0;

	boolean Func(S3Object obj) throws ObjectException;
}
