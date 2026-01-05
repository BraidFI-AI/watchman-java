"""
Configuration for Nemesis and Strategic Analyzer agents.
Set environment variables to customize AI provider and model.
"""
import os

# AI Provider Configuration
AI_PROVIDER = os.getenv('AI_PROVIDER', 'anthropic')  # 'anthropic', 'openai', 'ollama'
AI_MODEL = os.getenv('AI_MODEL', 'claude-sonnet-4-20250514')
AI_API_KEY = os.getenv('AI_API_KEY') or os.getenv('ANTHROPIC_API_KEY') or os.getenv('OPENAI_API_KEY')

# For local Ollama
OLLAMA_BASE_URL = os.getenv('OLLAMA_BASE_URL', 'http://localhost:11434')

# Watchman API Configuration
WATCHMAN_JAVA_API_URL = os.getenv('WATCHMAN_JAVA_API_URL', 'http://localhost:8080')
WATCHMAN_GO_API_URL = os.getenv('WATCHMAN_GO_API_URL', 'http://localhost:8081')

# Comparison Settings
COMPARE_IMPLEMENTATIONS = os.getenv('COMPARE_IMPLEMENTATIONS', 'true').lower() == 'true'
GO_IS_BASELINE = os.getenv('GO_IS_BASELINE', 'true').lower() == 'true'  # Treat Go as "correct"

# Report Storage
REPORT_DIR = os.getenv('REPORT_DIR', '/data/reports')
LOG_DIR = os.getenv('LOG_DIR', '/data/logs')

# GitHub Integration (optional)
GITHUB_TOKEN = os.getenv('GITHUB_TOKEN')
GITHUB_REPO = os.getenv('GITHUB_REPO', 'moov-io/watchman-java')
CREATE_GITHUB_ISSUES = os.getenv('CREATE_GITHUB_ISSUES', 'true').lower() == 'true'

# Agent Behavior
NEMESIS_MAX_ISSUES = int(os.getenv('NEMESIS_MAX_ISSUES', '50'))
NEMESIS_MIN_PRIORITY = os.getenv('NEMESIS_MIN_PRIORITY', 'P3')  # Include P3 and above

def get_ai_client():
    """Get configured AI client based on provider."""
    if AI_PROVIDER == 'anthropic':
        import anthropic
        return anthropic.Anthropic(api_key=AI_API_KEY)
    elif AI_PROVIDER == 'openai':
        import openai
        return openai.OpenAI(api_key=AI_API_KEY)
    elif AI_PROVIDER == 'ollama':
        import openai
        return openai.OpenAI(
            api_key='ollama',  # Ollama doesn't need real key
            base_url=OLLAMA_BASE_URL
        )
    else:
        raise ValueError(f"Unknown AI provider: {AI_PROVIDER}")

def validate_config():
    """Validate required configuration."""
    errors = []
    
    if AI_PROVIDER in ['anthropic', 'openai'] and not AI_API_KEY:
        errors.append(f"AI_API_KEY required for provider: {AI_PROVIDER}")
    
    if not os.path.exists(REPORT_DIR):
        try:
            os.makedirs(REPORT_DIR, exist_ok=True)
        except Exception as e:
            errors.append(f"Cannot create report directory: {e}")
    
    if not os.path.exists(LOG_DIR):
        try:
            os.makedirs(LOG_DIR, exist_ok=True)
        except Exception as e:
            errors.append(f"Cannot create log directory: {e}")
    
    if errors:
        raise ValueError("\n".join(errors))

if __name__ == "__main__":
    print("Agent Configuration:")
    print(f"  AI Provider: {AI_PROVIDER}")
    print(f"  AI Model: {AI_MODEL}")
    print(f"  Java API: {WATCHMAN_JAVA_API_URL}")
    print(f"  Go API: {WATCHMAN_GO_API_URL}")
    print(f"  Compare Implementations: {COMPARE_IMPLEMENTATIONS}")
    print(f"  Go as Baseline: {GO_IS_BASELINE}")
    print(f"  Report Dir: {REPORT_DIR}")
    print(f"  Log Dir: {LOG_DIR}")
    
    try:
        validate_config()
        print("\n✓ Configuration valid")
    except ValueError as e:
        print(f"\n✗ Configuration errors:\n{e}")
        exit(1)
