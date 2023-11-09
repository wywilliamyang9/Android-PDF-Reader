package net.codebot.pdfviewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ImageView;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

enum ToolEnum{
    pencil,
    highlighter,
    eraser
}

@SuppressLint("AppCompatCustomView")
public class PDFimage extends ImageView {

    final String LOGNAME = "pdf_image";

    // current page number
    int pageNum;

    // Tools
    ToolEnum curr_tool = ToolEnum.pencil;
    Paint pencil, highlighter;

    // drawing path
    Path path = null;
    HashMap<Integer, ArrayList<Path>> pencilMap = new HashMap<>();
    HashMap<Integer, ArrayList<Path>> highlighterMap = new HashMap<>();
    HashMap<Integer, Stack<UndoRedo>> undoMap = new HashMap<>();
    HashMap<Integer, Stack<UndoRedo>> redoMap = new HashMap<>();

    // variables for pan and zoom
    float x1, x2, y1, y2, old_x1, old_y1, old_x2, old_y2;
    float mid_x = -1f, mid_y = -1f, old_mid_x = -1f, old_mid_y = -1f;
    int p1_id, p1_index, p2_id, p2_index;
    Matrix matrix = new Matrix();
    Matrix inverse = new Matrix();

    // zoom limits
    float total_scale = 1;
    float maxZoom = 10;
    float minZoom = 0.75f;

    // image to display
    Bitmap bitmap;

    // constructor
    public PDFimage(Context context) {
        super(context);
        // initialize pencil
        pencil = new Paint(Paint.ANTI_ALIAS_FLAG);
        pencil.setColor(Color.BLUE);
        pencil.setStyle(Paint.Style.STROKE);
        pencil .setStrokeWidth(5);

        // initialize highlighter
        highlighter = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlighter.setColor(Color.YELLOW);
        highlighter.setStyle(Paint.Style.STROKE);
        highlighter.setStrokeWidth(20);
        highlighter.setAlpha(200);
    }

    // capture touch events (down/move/up) to create a path
    // and use that to create a stroke that we can draw
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getPointerCount()) {
            case 1:
                switch (this.getTool()) {
                    case pencil:
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                Log.d("pencil", "Action down");
                                path = new Path();
                                path.moveTo(event.getX(), event.getY());
                                break;
                            case MotionEvent.ACTION_MOVE:
                                Log.d("pencil", "Action move");
                                path.lineTo(event.getX(), event.getY());
                                break;
                            case MotionEvent.ACTION_UP:
                                Log.d("pencil", "Action up");
                                pencilMap.get(pageNum).add(path);

                                // populate path array
                                ArrayList<Path> undo_array = new ArrayList<>();
                                undo_array.add(path);

                                // update undo and redo
                                UndoRedo undo = new UndoRedo(true, undo_array, new ArrayList<Path>());
                                undoMap.get(pageNum).push(undo);
                                redoMap.get(pageNum).clear();
                                break;
                        }
                        break;
                    case highlighter:
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                Log.d("highlighter", "Action down");
                                path = new Path();
                                path.moveTo(event.getX(), event.getY());
                                break;
                            case MotionEvent.ACTION_MOVE:
                                Log.d("highlighter", "Action move");
                                path.lineTo(event.getX(), event.getY());
                                break;
                            case MotionEvent.ACTION_UP:
                                Log.d("highlighter", "Action up");
                                highlighterMap.get(pageNum).add(path);

                                // populate path array
                                ArrayList<Path> undo_array = new ArrayList<>();
                                undo_array.add(path);

                                // update undo and redo
                                UndoRedo undo = new UndoRedo(true, new ArrayList<Path>(), undo_array);
                                undoMap.get(pageNum).push(undo);
                                redoMap.get(pageNum).clear();
                                break;
                        }
                        break;
                    case eraser:
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                Log.d("eraser", "Action down");
                                path = new Path();
                                path.moveTo(event.getX(), event.getY());
                                break;
                            case MotionEvent.ACTION_MOVE:
                                Log.d("eraser", "Action move");
                                path.lineTo(event.getX(), event.getY());
                                break;
                            case MotionEvent.ACTION_UP:
                                Log.d("eraser", "Action up");
                                // check intersection with pencil
                                ArrayList<Path> undo_pencil = new ArrayList<>();
                                for (Path pencil_path: pencilMap.get(pageNum)){
                                    Path temp = new Path(path);
                                    temp.op(pencil_path, Path.Op.INTERSECT);
                                    boolean intersects = temp.isEmpty();
                                    System.out.println("pencil " + intersects);
                                    if (!intersects) {
                                        undo_pencil.add(pencil_path);
                                    }
                                }

                                // check intersection with highlighter
                                ArrayList<Path> undo_highlighter = new ArrayList<>();
                                for (Path highlighter_path: highlighterMap.get(pageNum)){
                                    Path temp = new Path(path);
                                    temp.op(highlighter_path, Path.Op.INTERSECT);
                                    boolean intersects = temp.isEmpty();
                                    System.out.println("highlighter " + intersects);
                                    if (!intersects) {
                                        undo_highlighter.add(highlighter_path);
                                    }
                                }

                                // update undo and redo
                                if (!undo_pencil.isEmpty() || !undo_highlighter.isEmpty()) {
                                    UndoRedo undo = new UndoRedo(false, undo_pencil, undo_highlighter);
                                    undoMap.get(pageNum).push(undo);
                                    redoMap.get(pageNum).clear();
                                }

                                // erase pencil and highlighter
                                pencilMap.get(pageNum).removeAll(undo_pencil); // remove pencil
                                highlighterMap.get(pageNum).removeAll(undo_highlighter); // remove highlighter
                                break;
                        }
                        break;
                }
                break;
            case 2: // code from PanZoom example code
                // point 1
                p1_id = event.getPointerId(0);
                p1_index = event.findPointerIndex(p1_id);

                // mapPoints returns values in-place
                float[] inverted = new float[] { event.getX(p1_index), event.getY(p1_index) };
                inverse.mapPoints(inverted);

                // first pass, initialize the old == current value
                if (old_x1 < 0 || old_y1 < 0) {
                    old_x1 = x1 = inverted[0];
                    old_y1 = y1 = inverted[1];
                } else {
                    old_x1 = x1;
                    old_y1 = y1;
                    x1 = inverted[0];
                    y1 = inverted[1];
                }

                // point 2
                p2_id = event.getPointerId(1);
                p2_index = event.findPointerIndex(p2_id);

                // mapPoints returns values in-place
                inverted = new float[] { event.getX(p2_index), event.getY(p2_index) };
                inverse.mapPoints(inverted);

                // first pass, initialize the old == current value
                if (old_x2 < 0 || old_y2 < 0) {
                    old_x2 = x2 = inverted[0];
                    old_y2 = y2 = inverted[1];
                } else {
                    old_x2 = x2;
                    old_y2 = y2;
                    x2 = inverted[0];
                    y2 = inverted[1];
                }

                // midpoint
                mid_x = (x1 + x2) / 2;
                mid_y = (y1 + y2) / 2;
                old_mid_x = (old_x1 + old_x2) / 2;
                old_mid_y = (old_y1 + old_y2) / 2;

                // distance
                float d_old = (float) Math.sqrt(Math.pow((old_x1 - old_x2), 2) + Math.pow((old_y1 - old_y2), 2));
                float d = (float) Math.sqrt(Math.pow((x1 - x2), 2) + Math.pow((y1 - y2), 2));

                // pan and zoom during MOVE event
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    Log.d(LOGNAME, "Multitouch move");
                    // pan == translate of midpoint
                    float dx = mid_x - old_mid_x;
                    float dy = mid_y - old_mid_y;
                    matrix.preTranslate((1 / total_scale) * dx, (1 / total_scale) * dy);
                    Log.d(LOGNAME, "translate: " + ((1 / total_scale) * dx) + "," + ((1 / total_scale) * dy));

                    // zoom == change of spread between p1 and p2
                    float scale = d/d_old;
                    if (total_scale * scale > minZoom && total_scale * scale <= maxZoom){ // zoom limits
                        total_scale = total_scale * scale;
                        scale = Math.max(0, scale);
                        matrix.preScale(scale, scale, mid_x, mid_y);
                        Log.d(LOGNAME, "scale: " + scale);
                    }

                    // reset on up
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    old_x1 = -1f;
                    old_y1 = -1f;
                    old_x2 = -1f;
                    old_y2 = -1f;
                    old_mid_x = -1f;
                    old_mid_y = -1f;
                }
                break;
        }
        return true;
    }

    // set image as background
    public void setImage(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    // set brush characteristics
    // e.g. color, thickness, alpha
//    public void setBrush(Paint paint) { // not needed
//        this.curr_paint = paint;
//    }

    // set/update index of display page
    public void setPageNum(int num){
        pageNum = num;

        // initialize pencilMap for the new page
        if (!pencilMap.containsKey(num)){
            pencilMap.put(num, new ArrayList<Path>());
        }

        // initialize highlighterMap for the new page
        if (!highlighterMap.containsKey(num)){
            highlighterMap.put(num, new ArrayList<Path>());
        }

        // initialize undoMap for the new page
        if (!undoMap.containsKey(num)){
            undoMap.put(num, new Stack<UndoRedo>());
        }

        // initialize redoMap for the new page
        if (!redoMap.containsKey(num)){
            redoMap.put(num, new Stack<UndoRedo>());
        }

        matrix = new Matrix();
        inverse = new Matrix();
        total_scale = 1;
    }

    // set/update index of display page
    public void setTool(ToolEnum tool){
        curr_tool = tool;
    }

    // get curr_tool
    public ToolEnum getTool(){
        return curr_tool;
    }

    // get index of display page
    public int getPageNum(){
        return pageNum;
    }

    public void undo(){
        if (undoMap.get(pageNum).empty()){
            return;
        }
        UndoRedo undo = undoMap.get(pageNum).pop();
        for (Path path: undo.pencil_paths) {
            if (undo.visible == true) {
                pencilMap.get(pageNum).remove(path);
            } else {
                pencilMap.get(pageNum).add(path);
            }
        }
        for (Path path: undo.highlighter_paths) {
            if (undo.visible == true) {
                highlighterMap.get(pageNum).remove(path);
            } else {
                highlighterMap.get(pageNum).add(path);
            }
        }
        UndoRedo redo = new UndoRedo(!undo.visible, undo.pencil_paths, undo.highlighter_paths);
        redoMap.get(pageNum).push(redo);
    }

    public void redo(){
        if (redoMap.get(pageNum).empty()){
            return;
        }
        UndoRedo redo = redoMap.get(pageNum).pop();
        for (Path path: redo.pencil_paths) {
            if (redo.visible == true) {
                pencilMap.get(pageNum).remove(path);
            } else {
                pencilMap.get(pageNum).add(path);
            }
        }
        for (Path path: redo.highlighter_paths) {
            if (redo.visible == true) {
                highlighterMap.get(pageNum).remove(path);
            } else {
                highlighterMap.get(pageNum).add(path);
            }
        }
        UndoRedo undo = new UndoRedo(!redo.visible, redo.pencil_paths, redo.highlighter_paths);
        undoMap.get(pageNum).push(undo);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // draw background
        if (bitmap != null) {
            this.setImageBitmap(bitmap);
        }

        // apply transformations
        canvas.concat(matrix);

        // draw pencil lines
        for (Path path : pencilMap.get(pageNum)) {
            canvas.drawPath(path, pencil);
        }
        // draw highlighter lines
        for (Path path : highlighterMap.get(pageNum)) {
            canvas.drawPath(path, highlighter);
        }
        super.onDraw(canvas);
    }
}
