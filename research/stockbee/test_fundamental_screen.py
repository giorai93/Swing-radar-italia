import unittest
from fundamental_screen import evaluate,PROFILES

CUT='2025-09-01T12:00:00Z'
def fact(value):
 return dict(value=value,definition_verified=True,source_url='https://example.invalid/SYNTHETIC-TEST',period='synthetic-period',published_at='2025-08-01T12:00:00Z',available_at='2025-08-01T12:00:00Z')
def record():return {'issuer':'SYNTHETIC TEST ONLY','facts':{k:fact(v) for k,v in PROFILES['bucket2_eps_sales'].items()}}
class ScreenTests(unittest.TestCase):
 def test_missing_is_unknown(self):self.assertEqual(evaluate({},CUT)['profiles']['bucket1_growth']['status'],'unknown')
 def test_exact_threshold(self):self.assertEqual(evaluate(record(),CUT)['profiles']['bucket2_eps_sales']['status'],'pass')
 def test_future_unavailable(self):
  r=record();r['facts']['eps_growth_last_q_pct']['published_at']='2025-10-01T00:00:00Z';r['facts']['eps_growth_last_q_pct']['available_at']='2025-10-01T00:00:00Z'
  self.assertEqual(evaluate(r,CUT)['profiles']['bucket2_eps_sales']['status'],'unknown')
 def test_unverified_definition(self):
  r=record();r['facts']['eps_growth_last_q_pct']['definition_verified']=False
  self.assertEqual(evaluate(r,CUT)['profiles']['bucket2_eps_sales']['status'],'unknown')
 def test_known_failure(self):
  r=record();r['facts']['price_usd']['value']=.5;del r['facts']['eps_growth_last_q_pct']
  self.assertEqual(evaluate(r,CUT)['profiles']['bucket2_eps_sales']['status'],'fail')
 def test_mean_not_both_quarters(self):
  r=record();r['facts']['sales_growth_last_q_pct']['value']=60;r['facts']['avg_sales_growth_2q_pct']['value']=40
  # Previous growth could be 20; the screenshot does not require both >=39.
  self.assertEqual(evaluate(r,CUT)['profiles']['bucket1_growth']['status'],'pass')
 def test_no_provenance(self):
  r=record();del r['facts']['annual_sales_usd_m']['source_url']
  self.assertEqual(evaluate(r,CUT)['profiles']['bucket2_eps_sales']['status'],'unknown')
 def test_invalid_numbers(self):
  for bad in [True,float('nan'),'39']:
   r=record();r['facts']['eps_growth_last_q_pct']['value']=bad
   self.assertEqual(evaluate(r,CUT)['profiles']['bucket2_eps_sales']['status'],'unknown')
 def test_no_trade_signal(self):self.assertFalse(evaluate(record(),CUT)['profiles']['bucket2_eps_sales']['trade_signal'])
if __name__=='__main__':unittest.main()
