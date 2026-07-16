import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import zhTw from "element-plus/es/locale/lang/zh-tw";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";

import App from "./App.vue";
import router from "./router";
import "./styles.css";
import "element-plus/dist/index.css";

const app = createApp(App);

Object.entries(ElementPlusIconsVue).forEach(([key, component]) => {
  app.component(key, component);
});

app.use(createPinia());
app.use(router);
app.use(ElementPlus, { locale: zhTw });
app.mount("#app");
