package com.emr.gds;

import java.util.List;

import javafx.scene.control.TextArea;

public class FUChartManager {

	// FUChartManager.java
	public static void main(String[] args) {
	    IttiaApp app = new IttiaApp(); 
	    List<TextArea> allTextAreas = app.getTextAreas();

	    if (allTextAreas == null) {
	        System.out.println("allTextAreas is null.");
	    } else {
	        System.out.println("Number of TextAreas: " + allTextAreas.size());
	    }

	    if (allTextAreas != null && allTextAreas.size() > 8) {
	        // ... your code to get(8)
	    } else {
	        System.out.println("Error: The list of TextAreas is not valid or the required index (8) does not exist.");
	    }
	}
}

// Note: IttiaApp and its getTextAreas() method are assumed to be defined elsewhere.
// This code will only compile and run correctly if the IttiaApp class and its 
// methods are properly implemented.