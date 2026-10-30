package app.medialan;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;

public class SettingsActivity extends Activity {
    private EditText address;
    private TextView result;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_settings);
        address = findViewById(R.id.address);
        result = findViewById(R.id.result);
        address.setText(Catalog.server(this));
        findViewById(R.id.save).setOnClickListener(view -> test());
        findViewById(R.id.clear).setOnClickListener(view -> {
            UserStore.of(this).deleteAll();
            result.setText("Progresso e favoritos apagados.");
        });
        TextView order = findViewById(R.id.order);
        showOrder(order);
        order.setOnClickListener(view -> {
            List<String> orders = Arrays.asList(Catalog.ORDERS);
            int next = (orders.indexOf(Catalog.order(this)) + 1) % orders.size();
            Catalog.setOrder(this, orders.get(next));
            showOrder(order);
        });
        findViewById(R.id.save).requestFocus();
    }

    private void showOrder(TextView button) {
        int index = Arrays.asList(Catalog.ORDERS).indexOf(Catalog.order(this));
        button.setText("Ordem dos filmes: " + Catalog.NAMES_ORDERS[index]);
    }

    private void test() {
        String base = address.getText().toString().trim();
        Catalog.setServer(this, base);
        result.setText("Testando...");
        Catalog.inBackground(() -> new JSONObject(Catalog.download(Catalog.server(this) + "/api/health")), (data, error) -> {
            if (error != null) {
                result.setText("Sem resposta: " + error.getMessage());

                return;
            }
            result.setText("Conectado: " + data.optInt("movies") + " filmes, " + data.optInt("shows")
                    + " séries, " + data.optInt("episodes") + " episódios.");
        });
    }
}
