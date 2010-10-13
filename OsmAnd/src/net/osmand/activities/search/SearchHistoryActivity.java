package net.osmand.activities.search;

import java.util.List;

import net.osmand.OsmandSettings;
import net.osmand.R;
import net.osmand.activities.MapActivity;
import net.osmand.activities.search.SearchHistoryHelper.HistoryEntry;
import net.osmand.osm.LatLon;
import net.osmand.osm.MapUtils;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

public class SearchHistoryActivity extends ListActivity {
	private LatLon location;
	private SearchHistoryHelper helper;
	private Button clearButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ListView lv = new ListView(this);
		lv.setId(android.R.id.list);
		
		setContentView(lv);
		location = OsmandSettings.getLastKnownMapLocation(OsmandSettings.getPrefs(this));
		helper = SearchHistoryHelper.getInstance();
		
		
		clearButton = new Button(this);
		clearButton.setText(R.string.clear_all);
		clearButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				helper.removeAll(SearchHistoryActivity.this);
				setListAdapter(new HistoryAdapter(helper.getHistoryEntries(SearchHistoryActivity.this)));
			}
		});
	}
	@Override
	protected void onResume() {
		super.onResume();
		List<HistoryEntry> historyEntries = helper.getHistoryEntries(this);
		
		getListView().removeFooterView(clearButton);
		if (!historyEntries.isEmpty()) {
			getListView().addFooterView(clearButton);
		}
		setListAdapter(new HistoryAdapter(historyEntries));
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		HistoryEntry model = ((HistoryAdapter)getListAdapter()).getItem(position);
		selectModel(model);
	}

	private void selectModel(HistoryEntry model) {
		helper.selectEntry(model, this);
		OsmandSettings.setMapLocationToShow(this, model.getLat(), model.getLon());
		startActivity(new Intent(this, MapActivity.class));
	}
	
	
	class HistoryAdapter extends ArrayAdapter<HistoryEntry> {

		public HistoryAdapter(List<HistoryEntry> list) {
			super(SearchHistoryActivity.this, R.layout.search_history_list_item, list);
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			if (row == null) {
				LayoutInflater inflater = getLayoutInflater();
				row = inflater.inflate(R.layout.search_history_list_item, parent, false);
			}
			TextView label = (TextView) row.findViewById(R.id.label);
			TextView distanceLabel = (TextView) row.findViewById(R.id.distance_label);
			ImageButton icon = (ImageButton) row.findViewById(R.id.remove);
			final HistoryEntry model = getItem(position);
			if(location != null){
				int dist = (int) (MapUtils.getDistance(location, model.lat, model.lon));
				distanceLabel.setText(MapUtils.getFormattedDistance(dist));
			} else {
				distanceLabel.setText(""); //$NON-NLS-1$
			}
			label.setText(model.name);
			icon.setOnClickListener(new View.OnClickListener(){
				@Override
				public void onClick(View v) {
					helper.remove(model, SearchHistoryActivity.this);
					setListAdapter(new HistoryAdapter(helper.getHistoryEntries(SearchHistoryActivity.this)));
				}
				
			});
			View.OnClickListener clickListener = new View.OnClickListener(){
				@Override
				public void onClick(View v) {
					selectModel(model);
				}
				
			};
			distanceLabel.setOnClickListener(clickListener);
			label.setOnClickListener(clickListener);
			return row;
		}
	}

}
