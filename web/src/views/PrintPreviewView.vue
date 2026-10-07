<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, type PrintBill } from '../api'
import Icon from '../components/Icon.vue'

const route = useRoute()
const router = useRouter()
const bill = ref<PrintBill | null>(null)
const loading = ref(true)
const errorMsg = ref('')

const kind = String(route.params.type)
const id = String(route.params.id)

const BACK_PATH: Record<string, string> = {
  sales_order: '/orders/sales',
  purchase_order: '/orders/purchase',
  purchase_inbound: '/fulfillment/inbound',
  sales_outbound: '/fulfillment/outbound',
  payment: '/finance',
}

function print() {
  window.print()
}

async function downloadPdf() {
  const blob = await api.printPdfBlob(kind, id)
  api.saveBlob(blob, `${bill.value?.title}-${bill.value?.no}.pdf`)
}

onMounted(async () => {
  try {
    bill.value = await api.printBill(kind, id)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '加载单据失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <div class="bar no-print">
      <button class="btn" @click="router.push(BACK_PATH[kind] ?? '/dashboard')">
        <Icon name="chevron-left" :size="15" /> 返回
      </button>
      <div class="spacer" />
      <span class="hint">建议在打印对话框中选择「纸张 A4」「边距默认」，可另存为 PDF</span>
      <button class="btn" @click="downloadPdf"><Icon name="download" :size="15" /> 下载 PDF</button>
      <button class="btn btn-primary" @click="print"><Icon name="printer" :size="15" /> 打印</button>
    </div>

    <p v-if="errorMsg" class="err no-print">{{ errorMsg }}</p>
    <p v-if="loading" class="loading no-print">加载中…</p>

    <div v-if="bill" class="sheet">
      <header>
        <h1>{{ bill.title }}</h1>
        <div class="sub">{{ bill.company }}</div>
      </header>

      <div class="info">
        <div class="row">
          <div class="col"><span class="k">{{ bill.partyLabel }}</span><b>{{ bill.partyName }}</b></div>
          <div class="col"><span class="k">单据编号</span><b>{{ bill.no }}</b></div>
        </div>
        <div class="row">
          <div class="col"><span class="k">打印时间</span>{{ bill.printedAt }}</div>
          <div class="col"><span class="k">业务日期</span>{{ bill.date }}</div>
        </div>
        <div class="metas">
          <div v-for="(m, i) in bill.meta" :key="i" class="col">
            <span class="k">{{ m[0] }}</span>{{ m[1] }}
          </div>
        </div>
      </div>

      <table v-if="bill.items.length" class="grid">
        <thead>
          <tr>
            <th style="width: 40%">商品名称</th>
            <th>单位</th>
            <th class="r">数量</th>
            <th class="r">单价</th>
            <th class="r">金额</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(it, i) in bill.items" :key="i">
            <td>
              {{ it.name }}
              <span v-if="it.spec" class="spec">{{ it.spec }}</span>
            </td>
            <td>{{ it.unit || '—' }}</td>
            <td class="r">{{ it.qty }}</td>
            <td class="r">{{ it.price }}</td>
            <td class="r">{{ it.amount }}</td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <td colspan="2">合计</td>
            <td class="r">{{ bill.totalQty }}</td>
            <td />
            <td class="r"><b>{{ bill.totalAmount }}</b></td>
          </tr>
        </tfoot>
      </table>
      <div v-else class="amount-only">
        <span class="k">金额</span>
        <b class="big">{{ bill.totalAmount }}</b>
      </div>

      <div class="upper">金额大写：{{ bill.amountUpper }}</div>
      <div v-if="bill.remark" class="remark">备注：{{ bill.remark }}</div>

      <footer>
        <div>制单人：{{ bill.printedBy }}</div>
        <div>审核人：____________</div>
        <div>签收人：____________</div>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.bar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.spacer { flex: 1; }
.hint { font-size: 12px; color: var(--text-3); }
.err { background: var(--danger-light); color: #B91C1C; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; }
.loading { color: var(--text-3); padding: 30px 0; text-align: center; }

.sheet {
  width: 210mm; min-height: 240mm; margin: 0 auto; padding: 14mm 16mm;
  background: #fff; box-shadow: var(--shadow-lg); border-radius: 4px; color: #111;
}
.sheet header { text-align: center; margin-bottom: 16px; }
.sheet h1 { margin: 0; font-size: 24px; letter-spacing: 4px; }
.sheet .sub { font-size: 12px; color: #666; margin-top: 4px; }
.info { border: 1px solid #ccc; padding: 10px 12px; margin-bottom: 14px; font-size: 12.5px; }
.info .row { display: flex; gap: 20px; margin-bottom: 6px; }
.info .col { flex: 1; }
.metas { display: grid; grid-template-columns: repeat(2, 1fr); gap: 4px 20px; margin-top: 8px; padding-top: 8px; border-top: 1px dashed #ddd; }
.k { display: inline-block; width: 78px; color: #666; }
.grid { width: 100%; border-collapse: collapse; font-size: 12.5px; }
.grid th, .grid td { border: 1px solid #bbb; padding: 7px 8px; }
.grid th { background: #f3f4f6; text-align: left; font-weight: 700; }
.grid .r { text-align: right; }
.grid tfoot td { font-weight: 700; background: #f9fafb; }
.spec { display: block; font-size: 11px; color: #777; }
.amount-only { border: 1px solid #ccc; padding: 14px; font-size: 12.5px; }
.amount-only .big { font-size: 20px; }
.upper { margin-top: 14px; font-size: 13px; font-weight: 700; }
.remark { margin-top: 8px; font-size: 12.5px; color: #444; }
footer { display: flex; gap: 40px; margin-top: 48px; font-size: 12.5px; }

@media print {
  .no-print { display: none !important; }
  body { background: #fff; }
  .sheet { width: auto; min-height: auto; margin: 0; padding: 0; box-shadow: none; }
}

@page {
  size: A4;
  margin: 12mm;
}
</style>
