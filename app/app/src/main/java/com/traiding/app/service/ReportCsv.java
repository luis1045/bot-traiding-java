package com.traiding.app.service;

import com.traiding.app.web.dto.GridReportResponse;
import com.traiding.app.web.dto.LevelReportResponse;

import java.math.BigDecimal;

/**
 * Serializa un {@link GridReportResponse} a CSV (una fila por nivel + fila de totales).
 * Se usa coma como separador y punto como separador decimal (todas las cifras son {@code BigDecimal},
 * por lo que no hay ambigüedad de comas).
 */
public final class ReportCsv {

    private static final String SEP = ",";

    private ReportCsv() {
    }

    public static String toCsv(GridReportResponse report) {
        StringBuilder sb = new StringBuilder();

        sb.append("Nivel").append(SEP)
          .append("Banda inferior").append(SEP)
          .append("Banda superior").append(SEP)
          .append("% BTC").append(SEP)
          .append("Estado").append(SEP)
          .append("BTC asignado").append(SEP)
          .append("Compras").append(SEP)
          .append("Ventas").append(SEP)
          .append("BTC comprado").append(SEP)
          .append("BTC vendido").append(SEP)
          .append("USDT gastado").append(SEP)
          .append("USDT recibido").append(SEP)
          .append("PnL realizado USDT").append(SEP)
          .append("PnL realizado BTC").append(SEP)
          .append("PnL no realizado USDT").append(SEP)
          .append("PnL no realizado BTC").append(SEP)
          .append("PnL total USDT").append(SEP)
          .append("PnL total BTC")
          .append('\n');

        for (LevelReportResponse level : report.levels()) {
            sb.append(level.levelIndex()).append(SEP)
              .append(text(level.lowerPrice())).append(SEP)
              .append(text(level.upperPrice())).append(SEP)
              .append(text(level.weightPercent())).append(SEP)
              .append(level.holding() ? "en BTC" : "en USDT").append(SEP)
              .append(text(level.btcAllocated())).append(SEP)
              .append(level.buys()).append(SEP)
              .append(level.sells()).append(SEP)
              .append(text(level.btcBought())).append(SEP)
              .append(text(level.btcSold())).append(SEP)
              .append(text(level.usdtSpent())).append(SEP)
              .append(text(level.usdtReceived())).append(SEP)
              .append(text(level.realizedPnlUsdt())).append(SEP)
              .append(text(level.realizedPnlBtc())).append(SEP)
              .append(text(level.unrealizedPnlUsdt())).append(SEP)
              .append(text(level.unrealizedPnlBtc())).append(SEP)
              .append(text(level.totalPnlUsdt())).append(SEP)
              .append(text(level.totalPnlBtc()))
              .append('\n');
        }

        // Fila de totales: 12 columnas descriptivas vacías y luego los 6 acumulados de PnL.
        sb.append("TOTAL");
        for (int i = 0; i < 12; i++) {
            sb.append(SEP);
        }
        sb.append(text(report.realizedPnlUsdt())).append(SEP)
          .append(text(report.realizedPnlBtc())).append(SEP)
          .append(text(report.unrealizedPnlUsdt())).append(SEP)
          .append(text(report.unrealizedPnlBtc())).append(SEP)
          .append(text(report.totalPnlUsdt())).append(SEP)
          .append(text(report.totalPnlBtc()))
          .append('\n');

        return sb.toString();
    }

    private static String text(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
