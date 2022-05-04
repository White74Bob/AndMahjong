/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package wb.game.mahjong.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import wb.game.mahjong.R;
import wb.game.mahjong.R.styleable;

/**
 * A layout that arranges its children in a grid. The size of the cells is set
 * by the {@link #setCellSize} method and the android:cell_width and
 * android:cell_height attributes in XML. The number of rows and columns is
 * determined at runtime. Each cell contains exactly one view, and they flow in
 * the natural child order (the order in which they were added, or the index in
 * {@link #addViewAt}. Views can not span multiple cells.
 */
public class FixedGridLayout extends ViewGroup {
    private int mCellWidth;
    private int mCellHeight;

    public enum Position {
        Bottom,
        Right,
        Top,
        Left;

        static Position getPosition(int ordinal) {
            Position[] values = values();
            for (Position position : values) {
                if (position.ordinal() == ordinal) {
                    return position;
                }
            }
            return null;
        }
    }

    // 0 - bottom, 1 - right, 2 - top, 3 - left
    private final Position mPosition;

    public FixedGridLayout(Context context) {
        super(context);
        mPosition = null;
    }

    public FixedGridLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Read the resource attributes.
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FixedGridLayout);

        mCellWidth = a.getDimensionPixelSize(R.styleable.FixedGridLayout_cellWidth, -1);
        mCellHeight = a.getDimensionPixelSize(R.styleable.FixedGridLayout_cellHeight, -1);
        mPosition = Position.getPosition(a.getInt(R.styleable.FixedGridLayout_position,
                Position.Bottom.ordinal()));
        a.recycle();
    }

    public void setCellSize(int px, int py) {
        mCellWidth = px;
        mCellHeight = px;
        requestLayout();
    }

    public void addCell(View view) {
        addView(view);
        requestLayout();
    }

    public void removeCell(final int cellIndex) {
        final int cellCount = getChildCount();
        if (cellIndex >= cellCount || cellIndex < 0) {
            final String infoFormat = "Invalid cell index:%d, range[%d-%d]";
            throw new IllegalArgumentException(String.format(infoFormat, cellIndex, 0, cellCount));
        }
        removeViewAt(cellIndex);
        requestLayout();
    }

    public void clearCells() {
        final int cellCount = getChildCount();
        if (cellCount <= 0) return;
        removeAllViews();
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int cellWidthSpec = MeasureSpec.makeMeasureSpec(mCellWidth, MeasureSpec.AT_MOST);
        int cellHeightSpec = MeasureSpec.makeMeasureSpec(mCellHeight, MeasureSpec.AT_MOST);

        int count = getChildCount();
        for (int index = 0; index < count; index++) {
            final View child = getChildAt(index);
            child.measure(cellWidthSpec, cellHeightSpec);
        }
        // Use the size our parents gave us
        setMeasuredDimension(resolveSize(mCellWidth * count, widthMeasureSpec),
                resolveSize(mCellHeight * count, heightMeasureSpec));
    }

    private int getColumnNum(int w, int h, int cellWidth, int cellHeight) {
        int columnNum = 0;
        switch (mPosition) {
            case Bottom:
            case Top:
                columnNum = w / cellWidth;
                break;
            case Right:
            case Left:
                columnNum = h / cellHeight;
                break;
            default:
                throw new RuntimeException("Illegal position:" + mPosition);
        }
        if (columnNum <= 0) {
            columnNum = 1;
        }
        return columnNum;
    }

    private int getMaxRowNum(int w, int h, int cellWidth, int cellHeight) {
        int maxRowNum = 0;
        switch (mPosition) {
            case Bottom:
            case Top:
                maxRowNum = h / cellHeight;
                break;
            case Right:
            case Left:
                maxRowNum = w / cellWidth;
                break;
            default:
                throw new RuntimeException("Illegal position:" + mPosition);
        }
        if (maxRowNum <= 0) {
            maxRowNum = 1;
        }
        return maxRowNum;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int cellWidth = mCellWidth;
        int cellHeight = mCellHeight;

        final int w = right - left;
        final int h = bottom - top;
        int columns = getColumnNum(w, h, cellWidth, cellHeight);
        int maxRowNum = getMaxRowNum(w, h, cellWidth, cellHeight);

        int count = getChildCount();

        int measuredWidth, measuredHeight;
        int cellLeft, cellTop;
        for (int index = 0; index < count; index++) {
            final View child = getChildAt(index);

            measuredWidth = child.getMeasuredWidth();
            measuredHeight = child.getMeasuredHeight();

            int row = index / columns;
            int col = index % columns;

            cellLeft = getCellLeft(row, col, maxRowNum, columns) + ((cellWidth - measuredWidth) / 2);
            cellTop = getCellTop(row, col, maxRowNum, columns) + ((cellHeight - measuredHeight) / 2);

            child.layout(cellLeft, cellTop, cellLeft + measuredWidth, cellTop + measuredHeight);
        }
    }

    private int getCellLeft(int row, int col, int maxRowNum, int columnNum) {
        switch (mPosition) {
            case Bottom:
                return col * mCellWidth;
            case Right:
                return row * mCellWidth;
            case Top:
                return (columnNum - col - 1) * mCellWidth;
            case Left:
                return (maxRowNum - row - 1) * mCellWidth;
            default:
                throw new RuntimeException("Illegal position:" + mPosition);
        }
    }

    private int getCellTop(int row, int col, int maxRowNum, int columnNum) {
        switch (mPosition) {
            case Bottom:
                return row * mCellHeight;
            case Right:
                return (columnNum - col - 1) * mCellHeight;
            case Top:
                return (maxRowNum - row - 1) * mCellHeight;
            case Left:
                return col * mCellHeight;
            default:
                throw new RuntimeException("Illegal position:" + mPosition);
        }
    }
}
