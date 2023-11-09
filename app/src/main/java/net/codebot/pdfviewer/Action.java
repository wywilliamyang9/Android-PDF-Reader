package net.codebot.pdfviewer;

import android.graphics.Path;

import java.util.ArrayList;

class UndoRedo {
    Boolean visible;
    ArrayList<Path> pencil_paths;
    ArrayList<Path> highlighter_paths;

    public UndoRedo(boolean visible, ArrayList<Path> pencil_paths, ArrayList<Path>  highlighter_paths){
        this.visible = visible;
        this.pencil_paths = pencil_paths;
        this.highlighter_paths = highlighter_paths;
    }
}
