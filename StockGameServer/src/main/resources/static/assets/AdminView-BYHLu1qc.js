import { m as useAuthStore, J as useMarketStore, Q as useAdminStore, z as onMounted, o as openBlock, c as createElementBlock, u as unref, N as createBlock, F as Fragment, M as createVNode, R as withCtx, S as resolveComponent, y as reactive, I as createTextVNode, t as toDisplayString, g as formatPrice, a as createBaseVNode, D as formatDateTime, b as formatSignedMoney, L as roleLabel, f as formatMoney, d as formatPercent, P as ElMessage, l as computed } from "./index-CDzZhxnB.js";
const _hoisted_1 = { class: "space-y-6" };
const _hoisted_2 = { class: "flex items-center justify-between" };
const _hoisted_3 = { class: "grid gap-3" };
const _hoisted_4 = { class: "flex items-center justify-between" };
const _hoisted_5 = { class: "flex items-center justify-between" };
const _hoisted_6 = { class: "flex gap-2" };
const _sfc_main = {
  __name: "AdminView",
  setup(__props) {
    const authStore = useAuthStore();
    const marketStore = useMarketStore();
    const adminStore = useAdminStore();
    const simulatorForm = reactive({
      retailCount: 0,
      noiseCount: 0
    });
    const announcementForm = reactive({
      message: ""
    });
    const topBids = computed(() => {
      var _a, _b;
      return ((_b = (_a = marketStore.orderBook) == null ? void 0 : _a.bids) == null ? void 0 : _b.slice(0, 5)) || [];
    });
    const topAsks = computed(() => {
      var _a, _b;
      return ((_b = (_a = marketStore.orderBook) == null ? void 0 : _a.asks) == null ? void 0 : _b.slice(0, 5)) || [];
    });
    async function refreshAdmin() {
      await adminStore.bootstrap();
      if (adminStore.status) {
        simulatorForm.retailCount = adminStore.status.aiRetailCount;
        simulatorForm.noiseCount = adminStore.status.aiNoiseCount;
      }
    }
    async function runAction(action, successMessage) {
      try {
        await action();
        ElMessage.success(successMessage);
      } catch (error) {
        ElMessage.error(error.message);
      }
    }
    function toggleUser(row) {
      return runAction(
        () => adminStore.setUserEnabled(row.username, !row.enabled),
        `${row.username} 已${row.enabled ? "停用" : "啟用"}。`
      );
    }
    function resetAccount(row) {
      return runAction(
        () => adminStore.resetAccount(row.username),
        `${row.username} 的帳戶已重置。`
      );
    }
    function updateSimulator() {
      return runAction(
        () => adminStore.updateSimulatorConfig({
          retailCount: Number(simulatorForm.retailCount),
          noiseCount: Number(simulatorForm.noiseCount)
        }),
        "模擬器設定已更新。"
      );
    }
    function sendAnnouncement() {
      return runAction(
        async () => {
          await adminStore.sendAnnouncement(announcementForm.message);
          announcementForm.message = "";
        },
        "公告已送出。"
      );
    }
    onMounted(async () => {
      if (!authStore.isAdmin) return;
      await refreshAdmin();
    });
    return (_ctx, _cache) => {
      const _component_el_result = resolveComponent("el-result");
      const _component_el_alert = resolveComponent("el-alert");
      const _component_el_tag = resolveComponent("el-tag");
      const _component_el_descriptions_item = resolveComponent("el-descriptions-item");
      const _component_el_descriptions = resolveComponent("el-descriptions");
      const _component_el_card = resolveComponent("el-card");
      const _component_el_col = resolveComponent("el-col");
      const _component_el_button = resolveComponent("el-button");
      const _component_el_input_number = resolveComponent("el-input-number");
      const _component_el_form_item = resolveComponent("el-form-item");
      const _component_el_form = resolveComponent("el-form");
      const _component_el_row = resolveComponent("el-row");
      const _component_el_input = resolveComponent("el-input");
      const _component_el_table_column = resolveComponent("el-table-column");
      const _component_el_table = resolveComponent("el-table");
      const _component_el_tab_pane = resolveComponent("el-tab-pane");
      const _component_el_tabs = resolveComponent("el-tabs");
      return openBlock(), createElementBlock("section", _hoisted_1, [
        !unref(authStore).isAuthenticated ? (openBlock(), createBlock(_component_el_result, {
          key: 0,
          icon: "warning",
          title: "需要後台登入",
          "sub-title": "請使用 ADMIN 角色帳號登入後操作市場控制功能。"
        })) : !unref(authStore).isAdmin ? (openBlock(), createBlock(_component_el_result, {
          key: 1,
          icon: "error",
          title: "權限不足",
          "sub-title": "此頁面僅限 ADMIN 使用者存取。"
        })) : (openBlock(), createElementBlock(Fragment, { key: 2 }, [
          createVNode(_component_el_alert, {
            title: "後台控制台已啟用",
            type: "success",
            closable: false,
            description: "此頁已串接真實後台 API，可控制市場、調整模擬器、發送公告、啟用或停用使用者，以及重置玩家帳戶。",
            "show-icon": ""
          }),
          createVNode(_component_el_row, { gutter: 16 }, {
            default: withCtx(() => [
              createVNode(_component_el_col, {
                xs: 24,
                md: 8
              }, {
                default: withCtx(() => [
                  createVNode(_component_el_card, { shadow: "never" }, {
                    header: withCtx(() => {
                      var _a;
                      return [
                        createBaseVNode("div", _hoisted_2, [
                          _cache[7] || (_cache[7] = createBaseVNode("span", null, "市場狀態", -1)),
                          createVNode(_component_el_tag, {
                            type: ((_a = unref(adminStore).status) == null ? void 0 : _a.marketOpen) ? "success" : "danger"
                          }, {
                            default: withCtx(() => {
                              var _a2;
                              return [
                                createTextVNode(toDisplayString(((_a2 = unref(adminStore).status) == null ? void 0 : _a2.marketOpen) ? "開盤" : "休市"), 1)
                              ];
                            }),
                            _: 1
                          }, 8, ["type"])
                        ])
                      ];
                    }),
                    default: withCtx(() => [
                      createVNode(_component_el_descriptions, {
                        column: 1,
                        border: ""
                      }, {
                        default: withCtx(() => [
                          createVNode(_component_el_descriptions_item, { label: "股票代號" }, {
                            default: withCtx(() => {
                              var _a, _b;
                              return [
                                createTextVNode(toDisplayString(((_a = unref(adminStore).status) == null ? void 0 : _a.symbol) || ((_b = unref(marketStore).quote) == null ? void 0 : _b.symbol) || "--"), 1)
                              ];
                            }),
                            _: 1
                          }),
                          createVNode(_component_el_descriptions_item, { label: "最新價" }, {
                            default: withCtx(() => [
                              createTextVNode(toDisplayString(unref(adminStore).status ? unref(formatPrice)(unref(adminStore).status.lastPrice) : "--"), 1)
                            ]),
                            _: 1
                          }),
                          createVNode(_component_el_descriptions_item, { label: "最佳買 / 賣" }, {
                            default: withCtx(() => [
                              createTextVNode(toDisplayString(unref(adminStore).status ? `${unref(formatPrice)(unref(adminStore).status.bestBid)} / ${unref(formatPrice)(unref(adminStore).status.bestAsk)}` : "--"), 1)
                            ]),
                            _: 1
                          }),
                          createVNode(_component_el_descriptions_item, { label: "成交量" }, {
                            default: withCtx(() => {
                              var _a, _b;
                              return [
                                createTextVNode(toDisplayString(((_b = (_a = unref(adminStore).status) == null ? void 0 : _a.totalVolume) == null ? void 0 : _b.toLocaleString("zh-TW")) || "--"), 1)
                              ];
                            }),
                            _: 1
                          }),
                          createVNode(_component_el_descriptions_item, { label: "Tick 間隔" }, {
                            default: withCtx(() => [
                              createTextVNode(toDisplayString(unref(adminStore).status ? `${unref(adminStore).status.tickIntervalMs} ms` : "--"), 1)
                            ]),
                            _: 1
                          }),
                          createVNode(_component_el_descriptions_item, { label: "帳戶數" }, {
                            default: withCtx(() => {
                              var _a;
                              return [
                                createTextVNode(toDisplayString(((_a = unref(adminStore).status) == null ? void 0 : _a.accountCount) || 0), 1)
                              ];
                            }),
                            _: 1
                          })
                        ]),
                        _: 1
                      })
                    ]),
                    _: 1
                  })
                ]),
                _: 1
              }),
              createVNode(_component_el_col, {
                xs: 24,
                md: 8
              }, {
                default: withCtx(() => [
                  createVNode(_component_el_card, { shadow: "never" }, {
                    header: withCtx(() => [..._cache[8] || (_cache[8] = [
                      createBaseVNode("span", null, "市場控制", -1)
                    ])]),
                    default: withCtx(() => [
                      createBaseVNode("div", _hoisted_3, [
                        createVNode(_component_el_button, {
                          type: "success",
                          loading: unref(adminStore).loading,
                          onClick: _cache[0] || (_cache[0] = ($event) => runAction(() => unref(adminStore).setMarket(true), "市場已開盤。"))
                        }, {
                          default: withCtx(() => [..._cache[9] || (_cache[9] = [
                            createTextVNode(" 開盤 ", -1)
                          ])]),
                          _: 1
                        }, 8, ["loading"]),
                        createVNode(_component_el_button, {
                          type: "danger",
                          plain: "",
                          loading: unref(adminStore).loading,
                          onClick: _cache[1] || (_cache[1] = ($event) => runAction(() => unref(adminStore).setMarket(false), "市場已休市。"))
                        }, {
                          default: withCtx(() => [..._cache[10] || (_cache[10] = [
                            createTextVNode(" 休市 ", -1)
                          ])]),
                          _: 1
                        }, 8, ["loading"]),
                        createVNode(_component_el_button, {
                          type: "primary",
                          plain: "",
                          loading: unref(adminStore).loading,
                          onClick: _cache[2] || (_cache[2] = ($event) => runAction(() => unref(adminStore).triggerTick(), "已執行手動 tick。"))
                        }, {
                          default: withCtx(() => [..._cache[11] || (_cache[11] = [
                            createTextVNode(" 執行手動 Tick ", -1)
                          ])]),
                          _: 1
                        }, 8, ["loading"]),
                        createVNode(_component_el_button, {
                          plain: "",
                          loading: unref(adminStore).loading,
                          onClick: _cache[3] || (_cache[3] = ($event) => runAction(refreshAdmin, "後台資料已重新整理。"))
                        }, {
                          default: withCtx(() => [..._cache[12] || (_cache[12] = [
                            createTextVNode(" 重新整理 ", -1)
                          ])]),
                          _: 1
                        }, 8, ["loading"])
                      ])
                    ]),
                    _: 1
                  })
                ]),
                _: 1
              }),
              createVNode(_component_el_col, {
                xs: 24,
                md: 8
              }, {
                default: withCtx(() => [
                  createVNode(_component_el_card, { shadow: "never" }, {
                    header: withCtx(() => [..._cache[13] || (_cache[13] = [
                      createBaseVNode("span", null, "模擬器設定", -1)
                    ])]),
                    default: withCtx(() => [
                      createVNode(_component_el_form, { "label-position": "top" }, {
                        default: withCtx(() => [
                          createVNode(_component_el_form_item, { label: "散戶 AI 數量" }, {
                            default: withCtx(() => [
                              createVNode(_component_el_input_number, {
                                modelValue: simulatorForm.retailCount,
                                "onUpdate:modelValue": _cache[4] || (_cache[4] = ($event) => simulatorForm.retailCount = $event),
                                min: 0,
                                max: 50,
                                class: "!w-full"
                              }, null, 8, ["modelValue"])
                            ]),
                            _: 1
                          }),
                          createVNode(_component_el_form_item, { label: "雜訊交易 AI 數量" }, {
                            default: withCtx(() => [
                              createVNode(_component_el_input_number, {
                                modelValue: simulatorForm.noiseCount,
                                "onUpdate:modelValue": _cache[5] || (_cache[5] = ($event) => simulatorForm.noiseCount = $event),
                                min: 0,
                                max: 20,
                                class: "!w-full"
                              }, null, 8, ["modelValue"])
                            ]),
                            _: 1
                          }),
                          createVNode(_component_el_button, {
                            type: "primary",
                            loading: unref(adminStore).loading,
                            onClick: updateSimulator
                          }, {
                            default: withCtx(() => [..._cache[14] || (_cache[14] = [
                              createTextVNode(" 儲存設定 ", -1)
                            ])]),
                            _: 1
                          }, 8, ["loading"])
                        ]),
                        _: 1
                      })
                    ]),
                    _: 1
                  })
                ]),
                _: 1
              })
            ]),
            _: 1
          }),
          createVNode(_component_el_row, { gutter: 16 }, {
            default: withCtx(() => [
              createVNode(_component_el_col, {
                xs: 24,
                lg: 14
              }, {
                default: withCtx(() => [
                  createVNode(_component_el_card, { shadow: "never" }, {
                    header: withCtx(() => [
                      createBaseVNode("div", _hoisted_4, [
                        _cache[15] || (_cache[15] = createBaseVNode("span", null, "廣播公告", -1)),
                        createVNode(_component_el_tag, { type: "info" }, {
                          default: withCtx(() => [
                            createTextVNode(toDisplayString(unref(marketStore).latestAnnouncement || "尚無近期公告"), 1)
                          ]),
                          _: 1
                        })
                      ])
                    ]),
                    default: withCtx(() => [
                      createVNode(_component_el_form, { "label-position": "top" }, {
                        default: withCtx(() => [
                          createVNode(_component_el_form_item, { label: "公告內容" }, {
                            default: withCtx(() => [
                              createVNode(_component_el_input, {
                                modelValue: announcementForm.message,
                                "onUpdate:modelValue": _cache[6] || (_cache[6] = ($event) => announcementForm.message = $event),
                                type: "textarea",
                                rows: 4,
                                maxlength: "200",
                                "show-word-limit": "",
                                placeholder: "輸入要廣播給所有連線玩家的簡短訊息。"
                              }, null, 8, ["modelValue"])
                            ]),
                            _: 1
                          }),
                          createVNode(_component_el_button, {
                            type: "primary",
                            loading: unref(adminStore).loading,
                            disabled: !announcementForm.message.trim(),
                            onClick: sendAnnouncement
                          }, {
                            default: withCtx(() => [..._cache[16] || (_cache[16] = [
                              createTextVNode(" 發送公告 ", -1)
                            ])]),
                            _: 1
                          }, 8, ["loading", "disabled"])
                        ]),
                        _: 1
                      })
                    ]),
                    _: 1
                  })
                ]),
                _: 1
              }),
              createVNode(_component_el_col, {
                xs: 24,
                lg: 10
              }, {
                default: withCtx(() => [
                  createVNode(_component_el_card, { shadow: "never" }, {
                    header: withCtx(() => [..._cache[17] || (_cache[17] = [
                      createBaseVNode("span", null, "最佳五檔", -1)
                    ])]),
                    default: withCtx(() => [
                      createVNode(_component_el_tabs, null, {
                        default: withCtx(() => [
                          createVNode(_component_el_tab_pane, { label: "買盤" }, {
                            default: withCtx(() => [
                              createVNode(_component_el_table, {
                                data: topBids.value,
                                size: "small"
                              }, {
                                default: withCtx(() => [
                                  createVNode(_component_el_table_column, {
                                    prop: "price",
                                    label: "價格"
                                  }, {
                                    default: withCtx(({ row }) => [
                                      createTextVNode(toDisplayString(unref(formatPrice)(row.price)), 1)
                                    ]),
                                    _: 1
                                  }),
                                  createVNode(_component_el_table_column, {
                                    prop: "quantity",
                                    label: "數量"
                                  }),
                                  createVNode(_component_el_table_column, {
                                    prop: "orderCount",
                                    label: "筆數"
                                  })
                                ]),
                                _: 1
                              }, 8, ["data"])
                            ]),
                            _: 1
                          }),
                          createVNode(_component_el_tab_pane, { label: "賣盤" }, {
                            default: withCtx(() => [
                              createVNode(_component_el_table, {
                                data: topAsks.value,
                                size: "small"
                              }, {
                                default: withCtx(() => [
                                  createVNode(_component_el_table_column, {
                                    prop: "price",
                                    label: "價格"
                                  }, {
                                    default: withCtx(({ row }) => [
                                      createTextVNode(toDisplayString(unref(formatPrice)(row.price)), 1)
                                    ]),
                                    _: 1
                                  }),
                                  createVNode(_component_el_table_column, {
                                    prop: "quantity",
                                    label: "數量"
                                  }),
                                  createVNode(_component_el_table_column, {
                                    prop: "orderCount",
                                    label: "筆數"
                                  })
                                ]),
                                _: 1
                              }, 8, ["data"])
                            ]),
                            _: 1
                          })
                        ]),
                        _: 1
                      })
                    ]),
                    _: 1
                  })
                ]),
                _: 1
              })
            ]),
            _: 1
          }),
          createVNode(_component_el_row, { gutter: 16 }, {
            default: withCtx(() => [
              createVNode(_component_el_col, {
                xs: 24,
                lg: 12
              }, {
                default: withCtx(() => [
                  createVNode(_component_el_card, { shadow: "never" }, {
                    header: withCtx(() => [..._cache[18] || (_cache[18] = [
                      createBaseVNode("span", null, "近期成交", -1)
                    ])]),
                    default: withCtx(() => [
                      createVNode(_component_el_table, {
                        data: unref(marketStore).recentTrades.slice(0, 15),
                        size: "small"
                      }, {
                        default: withCtx(() => [
                          createVNode(_component_el_table_column, {
                            prop: "executedAt",
                            label: "時間",
                            "min-width": "180"
                          }, {
                            default: withCtx(({ row }) => [
                              createTextVNode(toDisplayString(unref(formatDateTime)(row.executedAt)), 1)
                            ]),
                            _: 1
                          }),
                          createVNode(_component_el_table_column, {
                            prop: "price",
                            label: "價格"
                          }, {
                            default: withCtx(({ row }) => [
                              createTextVNode(toDisplayString(unref(formatPrice)(row.price)), 1)
                            ]),
                            _: 1
                          }),
                          createVNode(_component_el_table_column, {
                            prop: "quantity",
                            label: "數量"
                          }),
                          createVNode(_component_el_table_column, {
                            prop: "buyerInitiated",
                            label: "主動方"
                          }, {
                            default: withCtx(({ row }) => [
                              createTextVNode(toDisplayString(row.buyerInitiated ? "買方" : "賣方"), 1)
                            ]),
                            _: 1
                          })
                        ]),
                        _: 1
                      }, 8, ["data"])
                    ]),
                    _: 1
                  })
                ]),
                _: 1
              }),
              createVNode(_component_el_col, {
                xs: 24,
                lg: 12
              }, {
                default: withCtx(() => [
                  createVNode(_component_el_card, { shadow: "never" }, {
                    header: withCtx(() => [..._cache[19] || (_cache[19] = [
                      createBaseVNode("span", null, "排行榜", -1)
                    ])]),
                    default: withCtx(() => [
                      createVNode(_component_el_table, {
                        data: unref(marketStore).leaderboard.slice(0, 10),
                        size: "small"
                      }, {
                        default: withCtx(() => [
                          createVNode(_component_el_table_column, {
                            prop: "rank",
                            label: "#",
                            width: "60"
                          }),
                          createVNode(_component_el_table_column, {
                            prop: "username",
                            label: "玩家"
                          }),
                          createVNode(_component_el_table_column, {
                            prop: "realizedPnl",
                            label: "已實現損益"
                          }, {
                            default: withCtx(({ row }) => [
                              createTextVNode(toDisplayString(unref(formatSignedMoney)(row.realizedPnl)), 1)
                            ]),
                            _: 1
                          })
                        ]),
                        _: 1
                      }, 8, ["data"])
                    ]),
                    _: 1
                  })
                ]),
                _: 1
              })
            ]),
            _: 1
          }),
          createVNode(_component_el_card, { shadow: "never" }, {
            header: withCtx(() => [
              createBaseVNode("div", _hoisted_5, [
                _cache[20] || (_cache[20] = createBaseVNode("span", null, "玩家帳戶控制", -1)),
                createVNode(_component_el_tag, { type: "success" }, {
                  default: withCtx(() => [
                    createTextVNode(toDisplayString(unref(authStore).username), 1)
                  ]),
                  _: 1
                })
              ])
            ]),
            default: withCtx(() => [
              createVNode(_component_el_table, {
                data: unref(adminStore).accounts,
                size: "small"
              }, {
                default: withCtx(() => [
                  createVNode(_component_el_table_column, {
                    prop: "username",
                    label: "玩家",
                    "min-width": "130"
                  }),
                  createVNode(_component_el_table_column, {
                    prop: "role",
                    label: "角色",
                    width: "100"
                  }, {
                    default: withCtx(({ row }) => [
                      createVNode(_component_el_tag, {
                        type: row.role === "ADMIN" ? "danger" : "info"
                      }, {
                        default: withCtx(() => [
                          createTextVNode(toDisplayString(unref(roleLabel)(row.role)), 1)
                        ]),
                        _: 2
                      }, 1032, ["type"])
                    ]),
                    _: 1
                  }),
                  createVNode(_component_el_table_column, {
                    prop: "enabled",
                    label: "啟用",
                    width: "100"
                  }, {
                    default: withCtx(({ row }) => [
                      createVNode(_component_el_tag, {
                        type: row.enabled ? "success" : "warning"
                      }, {
                        default: withCtx(() => [
                          createTextVNode(toDisplayString(row.enabled ? "是" : "否"), 1)
                        ]),
                        _: 2
                      }, 1032, ["type"])
                    ]),
                    _: 1
                  }),
                  createVNode(_component_el_table_column, {
                    prop: "availableCash",
                    label: "現金",
                    "min-width": "120"
                  }, {
                    default: withCtx(({ row }) => [
                      createTextVNode(toDisplayString(unref(formatMoney)(row.availableCash)), 1)
                    ]),
                    _: 1
                  }),
                  createVNode(_component_el_table_column, {
                    prop: "totalAssets",
                    label: "資產",
                    "min-width": "120"
                  }, {
                    default: withCtx(({ row }) => [
                      createTextVNode(toDisplayString(unref(formatMoney)(row.totalAssets)), 1)
                    ]),
                    _: 1
                  }),
                  createVNode(_component_el_table_column, {
                    prop: "totalStocks",
                    label: "股數",
                    width: "90"
                  }),
                  createVNode(_component_el_table_column, {
                    prop: "realizedPnl",
                    label: "已實現",
                    "min-width": "120"
                  }, {
                    default: withCtx(({ row }) => [
                      createTextVNode(toDisplayString(unref(formatSignedMoney)(row.realizedPnl)), 1)
                    ]),
                    _: 1
                  }),
                  createVNode(_component_el_table_column, {
                    prop: "unrealizedPnl",
                    label: "未實現",
                    "min-width": "120"
                  }, {
                    default: withCtx(({ row }) => [
                      createTextVNode(toDisplayString(unref(formatSignedMoney)(row.unrealizedPnl)), 1)
                    ]),
                    _: 1
                  }),
                  createVNode(_component_el_table_column, {
                    prop: "returnRate",
                    label: "報酬率",
                    width: "100"
                  }, {
                    default: withCtx(({ row }) => [
                      createTextVNode(toDisplayString(unref(formatPercent)(row.returnRate)), 1)
                    ]),
                    _: 1
                  }),
                  createVNode(_component_el_table_column, {
                    prop: "lastLoginAt",
                    label: "最後登入",
                    "min-width": "180"
                  }, {
                    default: withCtx(({ row }) => [
                      createTextVNode(toDisplayString(unref(formatDateTime)(row.lastLoginAt)), 1)
                    ]),
                    _: 1
                  }),
                  createVNode(_component_el_table_column, {
                    fixed: "right",
                    label: "操作",
                    "min-width": "210"
                  }, {
                    default: withCtx(({ row }) => [
                      createBaseVNode("div", _hoisted_6, [
                        createVNode(_component_el_button, {
                          size: "small",
                          type: row.enabled ? "warning" : "success",
                          disabled: row.username === unref(authStore).username,
                          onClick: ($event) => toggleUser(row)
                        }, {
                          default: withCtx(() => [
                            createTextVNode(toDisplayString(row.enabled ? "停用" : "啟用"), 1)
                          ]),
                          _: 2
                        }, 1032, ["type", "disabled", "onClick"]),
                        createVNode(_component_el_button, {
                          size: "small",
                          type: "danger",
                          plain: "",
                          onClick: ($event) => resetAccount(row)
                        }, {
                          default: withCtx(() => [..._cache[21] || (_cache[21] = [
                            createTextVNode(" 重置 ", -1)
                          ])]),
                          _: 1
                        }, 8, ["onClick"])
                      ])
                    ]),
                    _: 1
                  })
                ]),
                _: 1
              }, 8, ["data"])
            ]),
            _: 1
          })
        ], 64))
      ]);
    };
  }
};
export {
  _sfc_main as default
};
