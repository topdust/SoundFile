package com.prplx.soundfile;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.Animation;
import android.widget.ViewAnimator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Animated_ImageButton extends androidx.appcompat.widget.AppCompatImageButton
{
    public static float BTN_PRESS_SCALE = 0.9f;
    public static float BTN_RELEASE_SCALE = 1f;
    public static int ANIM_PERSS_DURATION_MS = 150;
    public static int ANIM_RELEASE_DURATION_MS = ANIM_PERSS_DURATION_MS / 2;


    public Animated_ImageButton(@NonNull Context context, @Nullable AttributeSet attrs)
    {
        super(context, attrs);

        this.setOnTouchListener(new OnTouchListener()
        {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                switch (event.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                    {
                        v.animate().scaleX(BTN_PRESS_SCALE).scaleY(BTN_PRESS_SCALE).setDuration(ANIM_PERSS_DURATION_MS).start();
                        break;
                    }

                    case MotionEvent.ACTION_UP:
                    {
                        ViewPropertyAnimator view_animator = v.animate();
                        view_animator.scaleX(BTN_RELEASE_SCALE).scaleY(BTN_RELEASE_SCALE)
                                .setDuration(ANIM_RELEASE_DURATION_MS);

                        // call performClick() only if motion event pointer
                        // is in the bounds of the view
                        float event_x = event.getX();
                        float event_y = event.getY();

                        //if not in the bounds - resize and return
                        if( event_x < 0 || event_x > v.getMeasuredWidth()
                            || event_y < 0 || event_y > v.getMeasuredHeight())
                        {
                            view_animator.start();

                            return true;
                        }


                        view_animator.setListener(new Animator.AnimatorListener()
                        {
                            @Override
                            public void onAnimationStart(Animator animation)
                            {

                            }

                            @Override
                            public void onAnimationEnd(Animator animation)
                            {
                                view_animator.setListener(null); //for no stacking listeners

                                v.performClick();
                            }

                            @Override
                            public void onAnimationCancel(Animator animation) {

                            }

                            @Override
                            public void onAnimationRepeat(Animator animation) {

                            }
                        });

                        view_animator.start();

                        break;
                    }


                    case MotionEvent.ACTION_MOVE:
                    {
                        break;
                    }


                    case MotionEvent.ACTION_CANCEL:
                    {
                        v.animate().scaleX(BTN_RELEASE_SCALE).scaleY(BTN_RELEASE_SCALE).setDuration(ANIM_RELEASE_DURATION_MS).start();
                        break;
                    }
                }

                return true;
            }
        });
    }
}
