package com.reconciler.dataset;

import java.util.List;

/** Common surface of an imported order or payment row. */
public interface ImportedRow {

	List<String> getDataQualityFlags();
}
