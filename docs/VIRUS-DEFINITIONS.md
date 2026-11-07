# 病毒库来源与覆盖范围

精确 SHA-256 指标：92 条，来自 ESET/malware-ioc 的 samples.sha256 文件，提交 825b826c5cbffcc1e59c16581ffdf7d527e8b695，BSD-2-Clause。只收集哈希，不下载或执行恶意软件。

- ghostchat：2 条；https://raw.githubusercontent.com/eset/malware-ioc/825b826c5cbffcc1e59c16581ffdf7d527e8b695/ghostchat/samples.sha256
- aridspy：15 条；https://raw.githubusercontent.com/eset/malware-ioc/825b826c5cbffcc1e59c16581ffdf7d527e8b695/aridspy/samples.sha256
- vajraspy：13 条；https://raw.githubusercontent.com/eset/malware-ioc/825b826c5cbffcc1e59c16581ffdf7d527e8b695/vajraspy/samples.sha256
- promptspy：9 条；https://raw.githubusercontent.com/eset/malware-ioc/825b826c5cbffcc1e59c16581ffdf7d527e8b695/promptspy/samples.sha256
- ngate：25 条；https://raw.githubusercontent.com/eset/malware-ioc/825b826c5cbffcc1e59c16581ffdf7d527e8b695/ngate/samples.sha256
- gref：5 条；https://raw.githubusercontent.com/eset/malware-ioc/825b826c5cbffcc1e59c16581ffdf7d527e8b695/gref/samples.sha256
- gravityrat：3 条；https://raw.githubusercontent.com/eset/malware-ioc/825b826c5cbffcc1e59c16581ffdf7d527e8b695/gravityrat/samples.sha256
- evilvideo：1 条；https://raw.githubusercontent.com/eset/malware-ioc/825b826c5cbffcc1e59c16581ffdf7d527e8b695/evilvideo/samples.sha256
- hamkombat：19 条；https://raw.githubusercontent.com/eset/malware-ioc/825b826c5cbffcc1e59c16581ffdf7d527e8b695/hamkombat/samples.sha256

Bloom 筛查：内置 HypatiaDatabases 的 Guava 格式 SHA256 数据，估计约 1,169,799 条，17 次哈希探测。数量是位占用估计，不是精确计数，也不代表全是 Android 样本。命中只能用于待复核，不可据此确诊或自动卸载。

读取算法已通过与 Guava 原实现交叉比较：1,000 条正例、10,000 条其他哈希，以及现有数据库的 2,000 次结果一致。没有执行恶意样本，未证明对所有现实病毒的检出率。

数据库为随包快照，当前不具备经过签名验证的在线增量更新。
