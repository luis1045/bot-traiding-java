/* Panel del bot de grillas BTCUSDT — Vue 3 (CDN) consumiendo la API REST /api/... */
const { createApp, ref, reactive, computed, watch, onMounted } = Vue;

createApp({
    setup() {
        // ------------------------------------------------------------------ formato
        function fmt(value, decimals = 2) {
            if (value === null || value === undefined || value === "") return "—";
            const n = Number(value);
            if (Number.isNaN(n)) return "—";
            return n.toLocaleString("en-US", {
                minimumFractionDigits: decimals,
                maximumFractionDigits: decimals,
            });
        }
        const usdt = (v) => fmt(v, 2);
        const btc = (v) => fmt(v, 8);

        function signClass(v) {
            const n = Number(v);
            if (n > 0) return "pos";
            if (n < 0) return "neg";
            return "";
        }
        function statusClass(s) {
            return s === "EXHAUSTED" ? "exhausted" : s === "ACTIVE" ? "active" : "off";
        }
        function localDateTime(iso) {
            if (!iso) return "—";
            const d = new Date(iso);
            return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
        }
        function localTime(iso) {
            if (!iso) return "—";
            const d = new Date(iso);
            return Number.isNaN(d.getTime()) ? iso : d.toLocaleTimeString();
        }

        // ------------------------------------------------------------------ cliente API
        async function api(path, options = {}) {
            const res = await fetch(path, {
                headers: { "Content-Type": "application/json" },
                ...options,
            });
            if (!res.ok) {
                let msg = "HTTP " + res.status;
                try {
                    const body = await res.json();
                    msg = body.message || body.error || msg;
                } catch (ignored) { /* sin cuerpo */ }
                throw new Error(msg);
            }
            if (res.status === 204) return null;
            const text = await res.text();
            return text ? JSON.parse(text) : null;
        }

        // ------------------------------------------------------------------ aviso
        const toast = reactive({ message: "", kind: "" });
        let toastTimer = null;
        function notify(message, kind = "ok") {
            toast.message = message;
            toast.kind = kind;
            clearTimeout(toastTimer);
            toastTimer = setTimeout(() => { toast.kind = ""; }, 3500);
        }

        // ------------------------------------------------------------------ estado
        const status = ref(null);
        const sets = ref([]);
        const alerts = ref([]);
        const selectedId = ref(null);
        const detail = reactive({ set: null, pnl: null, trades: [], report: null });
        const tickPrice = ref(83000);
        const quickTicks = [77000, 83000, 89000];
        const market = reactive({ symbol: "", price: null, time: null, source: "", error: "" });

        // Alertas ya vistas (para avisar solo de las nuevas) y formulario de registro manual.
        const knownAlerts = new Set();
        let alertsLoaded = false;
        const af = reactive({
            id: null, type: "", quantity: null, price: null,
            pnlUsdt: null, note: "", saving: false, pnlTouched: false,
        });

        // ------------------------------------------------------------------ tema (neumorfismo claro/oscuro)
        const THEME_KEY = "btc-grid-theme";
        const theme = ref(localStorage.getItem(THEME_KEY) || "dark");
        function applyTheme(t) {
            document.documentElement.dataset.theme = t;
            localStorage.setItem(THEME_KEY, t);
        }
        watch(theme, applyTheme, { immediate: true });
        function toggleTheme() {
            theme.value = theme.value === "dark" ? "light" : "dark";
        }

        const botRunning = computed(() => !!(status.value && status.value.running));
        const feed = computed(() => (status.value ? status.value.feed : "—"));
        const activeSets = computed(() => (status.value ? status.value.activeSets : 0));
        const lastPrice = computed(() =>
            status.value && status.value.lastPrice != null ? Number(status.value.lastPrice) : null);
        const marketSymbol = computed(() =>
            market.symbol || (status.value && status.value.symbol) || "BTCUSDT");

        // ------------------------------------------------------------------ porcentajes
        function distribute(weights) {
            const sum = weights.reduce((a, b) => a + b, 0);
            const out = weights.map((w) => Number(((w / sum) * 100).toFixed(4)));
            const diff = Number((100 - out.reduce((a, b) => a + b, 0)).toFixed(4));
            out[out.length - 1] = Number((out[out.length - 1] + diff).toFixed(4));
            return out;
        }
        const equalPercentsFor = (n) => distribute(new Array(Math.max(1, n)).fill(1));
        const rampPercentsFor = (n) => distribute(Array.from({ length: Math.max(1, n) }, (_, i) => i + 1));
        const sumOf = (arr) => arr.reduce((a, b) => a + (Number(b) || 0), 0);

        // ------------------------------------------------------------------ formulario crear
        const form = reactive({
            floorPrice: 78000,
            ceilPrice: 88000,
            levelCount: 5,
            btcInventory: 0.5,
            usdtReserve: 100000,
            feeRate: 0.001,
            startInBtc: true,
            percents: equalPercentsFor(5),
        });

        const formSum = computed(() => sumOf(form.percents));
        const formSumOk = computed(() => Math.abs(formSum.value - 100) <= 0.01);
        const bandHint = computed(() => {
            const f = Number(form.floorPrice), c = Number(form.ceilPrice), n = Number(form.levelCount);
            return f > 0 && c > f && n > 0 ? `${n} bandas de ${usdt((c - f) / n)} USDT` : "";
        });

        watch(() => form.levelCount, (n) => { form.percents = equalPercentsFor(n); });
        const equalPercents = () => { form.percents = equalPercentsFor(form.percents.length || form.levelCount); };
        const rampPercents = () => { form.percents = rampPercentsFor(form.percents.length || form.levelCount); };

        // ------------------------------------------------------------------ formulario nuevo conjunto
        const ns = reactive({ floor: null, ceil: null, count: null, btc: null, percents: [], show: false });
        const nsSum = computed(() => sumOf(ns.percents));
        const nsSumOk = computed(() => Math.abs(nsSum.value - 100) <= 0.01);
        watch(() => ns.count, (n) => { ns.percents = equalPercentsFor(n); });
        const equalNs = () => { ns.percents = equalPercentsFor(ns.percents.length || ns.count); };

        // ------------------------------------------------------------------ derivados del detalle
        const isExhausted = computed(() => !!detail.set && detail.set.status === "EXHAUSTED");
        const rangeText = computed(() => detail.set
            ? `${usdt(detail.set.floorPrice)} – ${usdt(detail.set.ceilPrice)} USDT · ${detail.set.levelCount} niveles · comisión ${detail.set.feeRate}`
            : "");
        function inZone(level) {
            const p = lastPrice.value;
            return p !== null && p >= Number(level.lowerPrice) && p <= Number(level.upperPrice);
        }
        /** Alerta pendiente de un nivel del conjunto seleccionado (o null). */
        function pendingAlertFor(levelIndex) {
            if (!selectedId.value) return null;
            return alerts.value.find((a) =>
                a.gridSetId === selectedId.value && a.levelIndex === levelIndex) || null;
        }

        watch(isExhausted, (v) => {
            if (v && detail.set && ns.floor == null) {
                ns.floor = detail.set.floorPrice;
                ns.ceil = detail.set.ceilPrice;
                ns.count = detail.set.levelCount;
                ns.btc = detail.set.btcInventory;
                ns.percents = equalPercentsFor(ns.count);
            }
        });

        // ------------------------------------------------------------------ cargas
        async function loadStatus() {
            try {
                status.value = await api("/api/bot/status");
            } catch (err) {
                notify("No se pudo leer el estado: " + err.message, "error");
            }
        }
        async function loadSets() {
            try {
                sets.value = await api("/api/grids");
            } catch (err) {
                notify("No se pudieron listar los conjuntos: " + err.message, "error");
            }
        }
        async function loadDetail(silent = false) {
            if (!selectedId.value) return;
            try {
                const id = selectedId.value;
                const [set, pnl, trades, report] = await Promise.all([
                    api(`/api/grids/${id}`),
                    api(`/api/grids/${id}/pnl`),
                    api(`/api/grids/${id}/trades`),
                    api(`/api/grids/${id}/report`),
                ]);
                detail.set = set;
                detail.pnl = pnl;
                detail.trades = trades;
                detail.report = report;
            } catch (err) {
                if (!silent) notify("No se pudo cargar el detalle: " + err.message, "error");
            }
        }
        async function selectSet(id) {
            selectedId.value = id;
            ns.floor = null;
            ns.show = false;
            closeAlert();
            await loadDetail();
        }
        async function loadAlerts(silent = true) {
            try {
                const list = await api("/api/alerts/pending");
                alerts.value = list || [];
                if (alertsLoaded) {
                    const fresh = alerts.value.filter((a) => !knownAlerts.has(a.id));
                    if (fresh.length) {
                        const a = fresh[0];
                        const extra = fresh.length > 1 ? ` (+${fresh.length - 1} más)` : "";
                        notify(`¡Zona de ${a.type === "BUY" ? "compra" : "venta"}! `
                            + `Conjunto #${a.gridSetId}, nivel ${a.levelIndex}${extra}`);
                    }
                }
                alerts.value.forEach((a) => knownAlerts.add(a.id));
                alertsLoaded = true;
                document.title = alerts.value.length
                    ? `(${alerts.value.length}) Alertas · Bot de grillas BTCUSDT`
                    : "Bot de grillas BTCUSDT";
            } catch (err) {
                if (!silent) notify("No se pudieron cargar las alertas: " + err.message, "error");
            }
        }
        async function refreshAll(silent = true) {
            await loadStatus();
            await loadSets();
            await loadAlerts(silent);
            if (selectedId.value) await loadDetail(silent);
        }

        // ------------------------------------------------------------------ acciones
        async function createGrid() {
            const n = Number(form.levelCount);
            const percents = form.percents.map(Number);
            if (percents.length !== n || percents.some((p) => Number.isNaN(p) || p <= 0)) {
                return notify("Cada nivel necesita un porcentaje mayor que cero.", "error");
            }
            if (!formSumOk.value) {
                return notify("Los porcentajes deben sumar 100%.", "error");
            }
            const body = {
                floorPrice: Number(form.floorPrice),
                ceilPrice: Number(form.ceilPrice),
                levelCount: n,
                btcInventory: Number(form.btcInventory),
                usdtReserve: Number(form.usdtReserve || 0),
                feeRate: Number(form.feeRate || 0),
                levelPercents: percents,
                startInBtc: form.startInBtc,
            };
            try {
                const created = await api("/api/grids", { method: "POST", body: JSON.stringify(body) });
                notify(`Conjunto #${created.id} creado.`);
                await loadSets();
                await selectSet(created.id);
            } catch (err) {
                notify("Error al crear: " + err.message, "error");
            }
        }

        async function sendTick(price) {
            const value = Number(price);
            if (!value || value <= 0) {
                return notify("Precio inválido.", "error");
            }
            try {
                await api("/api/bot/tick", { method: "POST", body: JSON.stringify({ price: value }) });
                tickPrice.value = value;
                notify("Precio " + usdt(value) + " procesado.");
                await refreshAll();
            } catch (err) {
                notify("Error al procesar el precio: " + err.message, "error");
            }
        }

        async function loadMarketPrice() {
            try {
                const p = await api("/api/market/price");
                market.symbol = p.symbol;
                market.price = p.price;
                market.time = p.time;
                market.source = p.source;
                market.error = "";
            } catch (err) {
                market.error = err.message;
            }
        }

        function injectMarketPrice() {
            if (market.price) sendTick(market.price);
        }

        async function startBot() {
            try {
                await api("/api/bot/start", { method: "POST" });
                notify("Feed arrancado.");
                await loadStatus();
            } catch (err) {
                notify("Error al arrancar: " + err.message, "error");
            }
        }

        async function stopBot() {
            try {
                await api("/api/bot/stop", { method: "POST" });
                notify("Feed detenido.");
                await loadStatus();
            } catch (err) {
                notify("Error al detener: " + err.message, "error");
            }
        }

        async function resolveContinue() {
            try {
                await api(`/api/grids/${selectedId.value}/resolve`, {
                    method: "POST",
                    body: JSON.stringify({ action: "CONTINUE" }),
                });
                notify("Conjunto reanudado con los mismos niveles.");
                await refreshAll();
            } catch (err) {
                notify("Error: " + err.message, "error");
            }
        }

        async function resolveNewSet() {
            const n = Number(ns.count);
            const percents = ns.percents.map(Number);
            if (percents.length !== n || percents.some((p) => Number.isNaN(p) || p <= 0)) {
                return notify("Cada nivel necesita un porcentaje mayor que cero.", "error");
            }
            if (!nsSumOk.value) {
                return notify("Los porcentajes deben sumar 100%.", "error");
            }
            const body = {
                action: "NEW_SET",
                floorPrice: Number(ns.floor),
                ceilPrice: Number(ns.ceil),
                levelCount: n,
                btcInventory: Number(ns.btc),
                levelPercents: percents,
            };
            try {
                const created = await api(`/api/grids/${selectedId.value}/resolve`, {
                    method: "POST",
                    body: JSON.stringify(body),
                });
                notify(`Nuevo conjunto #${created.id} creado.`);
                selectedId.value = created.id;
                ns.floor = null;
                ns.show = false;
                await refreshAll();
            } catch (err) {
                notify("Error: " + err.message, "error");
            }
        }

        async function deleteSet(id) {
            if (!confirm(`¿Eliminar el conjunto #${id} con sus niveles y operaciones? No se puede deshacer.`)) {
                return;
            }
            try {
                await api(`/api/grids/${id}`, { method: "DELETE" });
                if (selectedId.value === id) {
                    selectedId.value = null;
                    detail.set = null;
                    detail.pnl = null;
                    detail.trades = [];
                    detail.report = null;
                }
                await loadSets();
                notify(`Conjunto #${id} eliminado.`);
            } catch (err) {
                notify("Error al eliminar: " + err.message, "error");
            }
        }

        // ------------------------------------------------------------------ alertas manuales
        function estimatePnl(alert, quantity, price) {
            const qty = Number(quantity);
            const p = Number(price);
            if (!qty || !p) return null;
            const feeRate = Number(alert.feeRate || 0);
            const entry = alert.entryPrice != null ? Number(alert.entryPrice) : p;
            const amount = p * qty;
            const costBase = entry * qty * (1 + feeRate);
            return amount - amount * feeRate - costBase;
        }

        function openAlert(alert) {
            af.id = alert.id;
            af.type = alert.type;
            af.quantity = Number(alert.suggestedQuantity);
            af.price = Number(alert.suggestedPrice);
            af.note = "";
            af.pnlTouched = false;
            af.saving = false;
            const pnl = alert.type === "SELL" ? estimatePnl(alert, af.quantity, af.price) : null;
            af.pnlUsdt = pnl === null ? null : Math.round(pnl * 100) / 100;
        }

        function closeAlert() {
            af.id = null;
            af.type = "";
            af.quantity = null;
            af.price = null;
            af.pnlUsdt = null;
            af.note = "";
            af.pnlTouched = false;
            af.saving = false;
        }

        function onPnlInput() {
            af.pnlTouched = true;
        }

        // Si el usuario cambia cantidad o precio, el profit estimado se recalcula mientras no lo
        // haya editado a mano.
        watch(() => [af.quantity, af.price], () => {
            if (af.type !== "SELL" || af.pnlTouched || !af.id) return;
            const alert = alerts.value.find((a) => a.id === af.id);
            if (!alert) return;
            const pnl = estimatePnl(alert, af.quantity, af.price);
            af.pnlUsdt = pnl === null ? null : Math.round(pnl * 100) / 100;
        });

        async function submitAlert() {
            if (!af.id) return;
            const type = af.type;
            const body = {
                quantityBtc: Number(af.quantity),
                price: Number(af.price),
                note: af.note ? af.note.trim() : null,
            };
            if (type === "SELL") {
                body.pnlUsdt = af.pnlUsdt === null || af.pnlUsdt === "" ? null : Number(af.pnlUsdt);
            }
            af.saving = true;
            try {
                try {
                    await api(`/api/alerts/${af.id}/approve-${type === "BUY" ? "buy" : "sell"}`,
                        { method: "POST", body: JSON.stringify(body) });
                } catch (err) {
                    const puedeForzar = type === "BUY" && /reserva/i.test(err.message);
                    if (!puedeForzar) throw err;
                    if (!confirm(err.message + "\n\n¿Registrar la compra igualmente?")) return;
                    await api(`/api/alerts/${af.id}/approve-buy`, {
                        method: "POST",
                        body: JSON.stringify({ ...body, allowOverdraft: true }),
                    });
                }
                notify(type === "BUY" ? "Compra registrada." : "Venta registrada.");
                closeAlert();
                await refreshAll();
            } catch (err) {
                notify("Error al registrar: " + err.message, "error");
            } finally {
                af.saving = false;
            }
        }

        async function dismissAlert(alert) {
            const tipo = alert.type === "BUY" ? "compra" : "venta";
            if (!confirm(`¿Descartar la alerta de ${tipo} del conjunto #${alert.gridSetId}, `
                + `nivel ${alert.levelIndex}? No se repetirá hasta que el precio salga de la zona.`)) {
                return;
            }
            try {
                await api(`/api/alerts/${alert.id}/dismiss`, { method: "POST" });
                if (af.id === alert.id) closeAlert();
                notify("Alerta descartada.");
                await refreshAll();
            } catch (err) {
                notify("Error al descartar: " + err.message, "error");
            }
        }

        // ------------------------------------------------------------------ ciclo de vida
        onMounted(async () => {
            await refreshAll(false);
            await loadMarketPrice();
            setInterval(() => refreshAll(true), 4000);
            setInterval(loadMarketPrice, 5000);
        });

        return {
            // estado
            status, sets, selectedId, detail, tickPrice, quickTicks, alerts,
            botRunning, feed, activeSets, lastPrice,
            // formularios
            form, ns, af, formSum, formSumOk, nsSum, nsSumOk, bandHint,
            equalPercents, rampPercents, equalNs,
            // detalle
            isExhausted, rangeText, inZone, pendingAlertFor,
            // mercado (Binance público)
            market, marketSymbol, loadMarketPrice, injectMarketPrice,
            // alertas manuales
            openAlert, closeAlert, submitAlert, dismissAlert, onPnlInput,
            // tema (neumorfismo claro/oscuro)
            theme, toggleTheme,
            // utilidades
            usdt, btc, fmt, signClass, statusClass, localDateTime, localTime,
            // acciones
            selectSet, createGrid, sendTick, startBot, stopBot,
            resolveContinue, resolveNewSet, deleteSet,
            // aviso
            toast,
        };
    },
}).mount("#app");
