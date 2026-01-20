package com.mustafafyp.guardianai.customviews;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomBarChartView extends View {

    private Paint barPaint;
    private Paint textPaint;
    private List<Long> dataPoints = new ArrayList<>();
    private List<String> labels = new ArrayList<>();
    private int barColor = Color.parseColor("#4CAF50"); // Green
    private int textColor = Color.parseColor("#757575"); // Grey

    public CustomBarChartView(Context context) {
        super(context);
        init();
    }

    public CustomBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint = new Paint();
        barPaint.setColor(barColor);
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(textColor);
        textPaint.setTextSize(30f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
    }

    public void setData(List<Long> data, List<String> weekLabels) {
        this.dataPoints = data;
        this.labels = weekLabels;
        invalidate(); // Redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (dataPoints == null || dataPoints.isEmpty()) return;

        float width = getWidth();
        float height = getHeight();
        float padding = 40f;
        float bottomLabelHeight = 60f;
        float availableHeight = height - padding - bottomLabelHeight;

        int numberOfBars = dataPoints.size();
        float barWidth = (width - (2 * padding)) / (numberOfBars * 2); // Bar width + spacing
        float spacing = barWidth; 
        
        long maxVal = 0;
        for (Long val : dataPoints) {
            if (val > maxVal) maxVal = val;
        }
        if (maxVal == 0) maxVal = 1; // Prevent divide by zero

        float startX = padding;

        for (int i = 0; i < numberOfBars; i++) {
            long val = dataPoints.get(i);
            float barHeight = (val / (float) maxVal) * availableHeight;
            
            // Allow for a minimum height so empty bars aren't invisible if there's data? 
            // Better to show 0 as 0.
            
            float left = startX + (i * (barWidth + spacing));
            float top = padding + (availableHeight - barHeight);
            float right = left + barWidth;
            float bottom = height - bottomLabelHeight;

            // Draw Bar (Rounded Corners)
            RectF rect = new RectF(left, top, right, bottom);
            canvas.drawRoundRect(rect, 10f, 10f, barPaint);

            // Draw Label
            if (i < labels.size()) {
                canvas.drawText(labels.get(i), left + (barWidth / 2), height - 15f, textPaint);
            }
        }
    }
}
