# License map

本 monorepo 包含不同许可范围，不能用一份根许可证概括全部内容。

| 范围 | 许可证 |
| --- | --- |
| `numen-api/` 与 `minecraft-numen/` 中除下述公共 API 外的上游源码及衍生修改 | LGPL-3.0；以各目录内 `LICENSE` 为准 |
| 上游明确指定的 `com.dwinovo.numen.api` 公共集成 API | MIT；严格以各目录内 `LICENSE-API` 的范围说明为准 |
| 根目录新建的 `README.md`、`UPSTREAMS.md`、`NOTICE.md`、`LICENSES.md`、`ASSET_EXCLUSIONS.md` 和 `.gitignore` | MIT；见 `LICENSES/MIT-Synergy.txt` |
| 上游视觉、音频、品牌、皮肤及其他原创素材 | All Rights Reserved；未导入，限制见各目录内 `LICENSE-ASSETS` |

注意：目录位置本身不会自动改变文件许可证。来自 LGPL 代码的修改仍为 LGPL；不能因为它与 MIT 文件同处一个 monorepo 就重新许可。
