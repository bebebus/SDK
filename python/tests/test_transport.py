# SPDX-License-Identifier: MIT

"""真实请求构建和传输错误分类，不连接外部服务。"""

import io
import json
import threading
import unittest
import urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest.mock import patch

from openapi_sdk import ApiError, Client, Config, Environment, TransportError, signer


class _Response:
    def __init__(self, body, status=200):
        self.body = body
        self.status = status

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def getcode(self):
        return self.status

    def read(self):
        return self.body


class TestTransport(unittest.TestCase):
    def setUp(self):
        self.client = Client(Config(
            merchant_no="M1", api_key="key", api_secret_pay="pay-secret",
            api_secret_payout="payout-secret", environment=Environment.SANDBOX,
            timeout=7,
        ))

    def test_request_is_signed_and_response_retains_original_sign(self):
        response = _Response(b'{"code":0,"data":{"order_no":"P1"}}')
        with patch.object(self.client._opener, "open", return_value=response) as send:
            result = self.client.pay_query(out_order_no="merchant-order")
        request = send.call_args.args[0]
        payload = json.loads(request.data)
        self.assertEqual(request.full_url, "http://127.0.0.1:3090/api/open/v1/merchant/pay/query")
        self.assertEqual(request.get_method(), "POST")
        self.assertEqual(send.call_args.kwargs["timeout"], 7)
        self.assertEqual(payload["merchant_no"], "M1")
        self.assertEqual(payload["out_order_no"], "merchant-order")
        self.assertEqual(payload["sign"], signer.sign(payload, "pay-secret"))
        self.assertEqual(result, {"order_no": "P1"})
        self.assertEqual(result.sign, payload["sign"])

    def test_http_error_preserves_status_and_body(self):
        error = urllib.error.HTTPError("https://example.test", 503, "unavailable", {}, io.BytesIO(b"retry"))
        with patch.object(self.client._opener, "open", side_effect=error), self.assertRaises(TransportError) as raised:
            self.client.pay_query(out_order_no="x")
        self.assertEqual(raised.exception.status_code, 503)
        self.assertEqual(raised.exception.body, "retry")
        self.assertIs(raised.exception.cause, error)

    def test_network_error_preserves_cause(self):
        error = urllib.error.URLError("connection refused")
        with patch.object(self.client._opener, "open", side_effect=error), self.assertRaises(TransportError) as raised:
            self.client.pay_query(out_order_no="x")
        self.assertIs(raised.exception.cause, error)

    def test_invalid_json_or_missing_envelope_is_transport_error(self):
        for body in (b"not-json", b'{}', b'[]'):
            with self.subTest(body=body), patch.object(self.client._opener, "open", return_value=_Response(body)):
                with self.assertRaises(TransportError) as raised:
                    self.client.pay_query(out_order_no="x")
                self.assertEqual(raised.exception.body, body.decode())
                self.assertEqual(raised.exception.status_code, 200)

    def test_business_error_and_raw_envelope(self):
        body = b'{"code":123,"message":"declined","data":{"reason":"x"}}'
        with patch.object(self.client._opener, "open", side_effect=lambda *_args, **_kwargs: _Response(body)):
            with self.assertRaises(ApiError) as raised:
                self.client.pay_query(out_order_no="x")
            raw = self.client.call_raw("/merchant/pay/query", {}, "pay-secret")
        self.assertEqual(raised.exception.code, 123)
        self.assertEqual(raised.exception.data, {"reason": "x"})
        self.assertEqual(raw["code"], 123)

    def test_redirects_never_reach_target(self):
        hits = []

        class Handler(BaseHTTPRequestHandler):
            status = 302

            def do_POST(self):
                if self.path == "/target":
                    hits.append(self.path)
                    self.send_response(200)
                    self.end_headers()
                    return
                self.send_response(self.status)
                self.send_header("Location", f"http://127.0.0.1:{self.server.server_port}/target")
                self.end_headers()

            def do_GET(self):
                hits.append(self.path)
                self.send_response(200)
                self.end_headers()

            def log_message(self, *_args):
                pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(thread.join, 2)
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        self.client = Client(Config(
            merchant_no="M1", api_key="key", api_secret_pay="secret",
            base_url=f"http://127.0.0.1:{server.server_port}", timeout=2,
        ))
        for status in (301, 302, 303, 307, 308):
            Handler.status = status
            with self.subTest(status=status), self.assertRaises(TransportError) as raised:
                self.client.pay_query(out_order_no="x")
            self.assertEqual(raised.exception.status_code, status)
        self.assertEqual(hits, [])


if __name__ == "__main__":
    unittest.main()
