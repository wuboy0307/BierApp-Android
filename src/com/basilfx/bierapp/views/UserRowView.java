package com.basilfx.bierapp.views;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Map.Entry;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.basilfx.bierapp.BierAppApplication;
import com.basilfx.bierapp.R;
import com.basilfx.bierapp.callbacks.OnProductClickListener;
import com.basilfx.bierapp.data.models.Product;
import com.basilfx.bierapp.data.models.User;
import com.basilfx.bierapp.utils.Convert;
import com.basilfx.bierapp.utils.FlowLayout;
import com.basilfx.bierapp.utils.ProductInfo;
import com.google.common.base.Strings;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

/**
 *
 * 
 * @author Bas Stottelaar
 */
public class UserRowView extends RelativeLayout implements OnGlobalLayoutListener {	
	private static final String LOG_TAG = "UserRowView";
	
	private static class ViewHolder { 
		private TextView name;
		private TextView score;
		private ImageView avatar;
		private FlowLayout container;
	}
	
	private int productWidth;
	
	private int lastWidth;
	
	private User user;
	
	private OnProductClickListener callback;
	
	public UserRowView(Context context) {
		super(context);
		
		// Inflate layout
		LayoutInflater.from(context).inflate(R.layout.listview_row_user, this);
		Convert convert = new Convert(context);
		
		this.setLayoutParams(new AbsListView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		this.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
		this.setPadding(convert.toPx(5), convert.toPx(5), convert.toPx(5), convert.toPx(5));
		
		this.productWidth = convert.toPx(65);
		
		// Build a ViewHolder
		final ViewHolder holder = new ViewHolder();
    	
    	// Find fields
    	holder.name = (TextView) this.findViewById(R.id.name);
		holder.score = (TextView) this.findViewById(R.id.score);
		holder.avatar = (ImageView) this.findViewById(R.id.avatar);
		holder.container = (FlowLayout) this.findViewById(R.id.container);
		
		// Add container width listener
		this.getViewTreeObserver().addOnGlobalLayoutListener(this);
		
		// Save the holder internally
    	this.setTag(holder);
	}
	
	public User getUser() {
		return this.user;
	}
	
	public void setUser(User user) {
		this.user = user;
	}
	
	public void setCallback(OnProductClickListener callback) {
		this.callback = callback;
	}
	
	public void refreshAll() {
		this.refreshAvatar();
        this.refreshUser();
        this.refreshProducts();
	}
	
	public void refreshAvatar() {
		ViewHolder holder = (ViewHolder) this.getTag();
		String url = user.getAvatarUrl();
		
		if (Strings.isNullOrEmpty(url) || BierAppApplication.badImageUrls.contains(url)) {
			url = "drawable://" + R.drawable.avatar_none;
		}
		
 		ImageLoader.getInstance().displayImage(url, holder.avatar, new SimpleImageLoadingListener() {
			@Override
			public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
				if (!Strings.isNullOrEmpty(imageUri) && failReason.getCause() instanceof FileNotFoundException) {
					BierAppApplication.badImageUrls.add(imageUri);
				}
			}
		});
	}
	
	public void refreshUser() {
		ViewHolder holder = (ViewHolder) this.getTag();
		
		// Set the name, score and XP
 		holder.name.setText(user.getName());
 		holder.score.setText(user.getXp() + " XP");
	}
	
	public void refreshProducts() {
		ViewHolder holder = (ViewHolder) this.getTag();
		
		// The width is required to display the products.
		if (this.lastWidth == 0) {
			if (holder.container.getWidth() > 0) {
				this.lastWidth = holder.container.getWidth();
			} else {
				this.post(new Runnable() {
					@Override
					public void run() {
						UserRowView.this.onGlobalLayout();
					}
				});
				
				this.invalidate();
				return;
			}
		}
		
		// Add products to the place holders
        final Map<Product, ProductInfo> productMap = user.getProducts();
        ProductInfo productInfoMore = new ProductInfo(0, 0);
        
        // Determine the number of products to show
        int maxProducts = (int) Math.floor(this.lastWidth / this.productWidth);
        int productCount = productMap.size();
        int childCount = holder.container.getChildCount();
        int index = 0;
        
        // Try to add as many product views as possible
        if (childCount < productCount) {
        	while (childCount < productCount && childCount < maxProducts) {
        		ProductView view = new ProductView(this.getContext());
        		
        		holder.container.addView(view);
        		childCount++;
        	}
        }
        
        // Re-determine number of children
        childCount = holder.container.getChildCount();
        ProductView more = null;
        
        // Determine if more button should be displayed
        if (productCount > childCount) {
        	more = (ProductView) holder.container.getChildAt(childCount - 1);
        	childCount = childCount - 1;
        }
        
        // Update product views
        for (Entry<Product, ProductInfo> item : productMap.entrySet()) {
        	if (index >= childCount) {
        		productInfoMore.setChange(productInfoMore.getChange() + item.getValue().getChange());
        		productInfoMore.setCount(productInfoMore.getCount() + item.getValue().getCount());
        	} else {
        		ProductView view = (ProductView) holder.container.getChildAt(index);
        		this.refreshProduct(view, item.getKey(), item.getValue(), false);
        	}
        	
        	// Increment index to next entry
        	index++;
        }
        
        // Show the more button if applicable
        if (more != null) {
        	more.setCount(productInfoMore.getCount());
        	more.setChange(productInfoMore.getChange());
        	more.setGuestProduct(this.user.getRole() == User.GUEST);
        	more.setProductMore();
        	
        	more.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					RelativeLayout outerView = new RelativeLayout(UserRowView.this.getContext());
					FlowLayout innerView = new FlowLayout(UserRowView.this.getContext());
					
					// Add some spacing
					Convert convert = new Convert(UserRowView.this.getContext());
					innerView.setHorizontalSpacing(convert.toPx(5));
					innerView.setVerticalSpacing(convert.toPx(5));
					
					// Add each product to the view
					for (Entry<Product, ProductInfo> item : productMap.entrySet()) {
						ProductView productView = new ProductView(UserRowView.this.getContext());
						UserRowView.this.refreshProduct(productView, item.getKey(), item.getValue(), true);
						innerView.addView(productView);
					}
					
					// Configure the outer view
					outerView.setGravity(Gravity.CENTER);
					outerView.addView(innerView);
					
					// Finnaly, show the dialog
	        		new AlertDialog.Builder(UserRowView.this.getContext())
	    		    	.setView(outerView)
	    		    	.setTitle("Alle producten")
	    		    	.setPositiveButton(R.string.sluiten, null)
	    		    	.show();
				}
			});
        }
	}
	
	public void refreshProduct(ProductView productView, ProductInfo productInfo) {
		productView.setChange(productInfo.getChange());
		productView.setCount(productInfo.getCount());
	}
	
	private void refreshProduct(final ProductView productView, final Product product, final ProductInfo productInfo, final boolean inDialog) {
		// Skip invisible views
		if (productView.getVisibility() != View.VISIBLE) {
			return;
		}
		
		productView.setGuestProduct(this.user.getRole() == User.GUEST);
		productView.setChange(productInfo.getChange());
		productView.setCount(productInfo.getCount());
		productView.setTitle(product.getTitle());
		productView.setProductLogo(product.getLogo());
		
		// Set on click handler
		productView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				UserRowView.this.callback.onProductClickListener(UserRowView.this, (ProductView) v, UserRowView.this.user, inDialog, product, 1);
			}
		});
		
		// Prevent long clicks
		productView.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(final View v) {
				final NumberPicker picker = new NumberPicker(UserRowView.this.getContext());
				
				picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
				picker.setMinValue(1);
				picker.setMaxValue(100);
				picker.setValue(1);
				
				// Show dialog
				new AlertDialog.Builder(UserRowView.this.getContext())
					.setTitle("Aantal " + product.getTitle() + " selecteren")
					.setView(picker)
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							UserRowView.this.callback.onProductClickListener(UserRowView.this, (ProductView) v, UserRowView.this.user, inDialog, product, picker.getValue());
						}
					})
					.show();
				
				return true;
			}
		});
	}

	@Override
	public void onGlobalLayout() {
		ViewHolder holder = (ViewHolder) this.getTag();
		
		int width = holder.container.getWidth();
		
		if (width != UserRowView.this.lastWidth) {
			UserRowView.this.lastWidth = width;
			UserRowView.this.refreshProducts();
		}
		
	}
}
