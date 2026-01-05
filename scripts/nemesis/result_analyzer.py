"""
Result Analyzer - Compares Java vs Go search results
Detects divergences and classifies by type and severity
"""

from dataclasses import dataclass
from typing import List, Dict, Optional
from enum import Enum


class DivergenceType(Enum):
    """Types of divergences between Java and Go implementations."""
    TOP_RESULT_DIFFERS = "top_result_differs"
    SCORE_DIFFERENCE = "score_difference"
    RESULT_ORDER_DIFFERS = "result_order_differs"
    JAVA_EXTRA_RESULT = "java_extra_result"
    GO_EXTRA_RESULT = "go_extra_result"


@dataclass
class Divergence:
    """Represents a divergence between Java and Go results."""
    type: DivergenceType
    severity: str  # "critical", "moderate", "minor"
    description: str
    java_data: Optional[Dict] = None
    go_data: Optional[Dict] = None
    score_difference: Optional[float] = None


class ResultAnalyzer:
    """Analyzes and compares Java vs Go search results."""
    
    def _get_entity_id(self, entity: Dict) -> Optional[str]:
        """
        Extract entity ID from either Java or Go response format.
        Java uses 'id', Go uses 'sourceID'.
        """
        return entity.get("id") or entity.get("sourceID")
    
    def _get_entity_name(self, entity: Dict) -> str:
        """Extract entity name, handling different formats."""
        # Java format
        if "name" in entity:
            return entity["name"]
        # Go format - check business, person, organization
        for key in ["business", "person", "organization"]:
            if entity.get(key) and entity[key].get("name"):
                return entity[key]["name"]
        return entity.get("sdnName", "Unknown")
    
    def compare(self, java_results: List[Dict], go_results: List[Dict]) -> List[Divergence]:
        """
        Compare Java and Go results, return list of divergences.
        
        Args:
            java_results: List of entity results from Java API
            go_results: List of entity results from Go API
            
        Returns:
            List of Divergence objects
        """
        divergences = []
        
        # Handle empty results
        if not java_results and not go_results:
            return divergences
        
        if not java_results and go_results:
            for result in go_results:
                divergences.append(Divergence(
                    type=DivergenceType.GO_EXTRA_RESULT,
                    severity="moderate",
                    description=f"Go returned result but Java didn't: {self._get_entity_name(result)}",
                    go_data=result
                ))
            return divergences
            
        if java_results and not go_results:
            for result in java_results:
                divergences.append(Divergence(
                    type=DivergenceType.JAVA_EXTRA_RESULT,
                    severity="moderate",
                    description=f"Java returned result but Go didn't: {self._get_entity_name(result)}",
                    java_data=result
                ))
            return divergences
        
        # Compare top results
        if java_results and go_results:
            java_top = java_results[0]
            go_top = go_results[0]
            
            java_top_id = self._get_entity_id(java_top)
            go_top_id = self._get_entity_id(go_top)
            
            if java_top_id != go_top_id:
                divergences.append(Divergence(
                    type=DivergenceType.TOP_RESULT_DIFFERS,
                    severity="critical",
                    description=f"Top result differs: Java={self._get_entity_name(java_top)} (ID:{java_top_id}) vs Go={self._get_entity_name(go_top)} (ID:{go_top_id})",
                    java_data=java_top,
                    go_data=go_top
                ))
        
        # Build ID maps for comparison (using normalized IDs)
        java_by_id = {self._get_entity_id(r): r for r in java_results if self._get_entity_id(r)}
        go_by_id = {self._get_entity_id(r): r for r in go_results if self._get_entity_id(r)}
        
        # Compare scores for matching entities
        for entity_id, java_entity in java_by_id.items():
            if entity_id in go_by_id:
                go_entity = go_by_id[entity_id]
                java_score = java_entity.get("match", 0)
                go_score = go_entity.get("match", 0)
                score_diff = abs(java_score - go_score)
                
                if score_diff > 0.10:
                    divergences.append(Divergence(
                        type=DivergenceType.SCORE_DIFFERENCE,
                        severity="critical",
                        description=f"Large score difference for {self._get_entity_name(java_entity)}: {score_diff:.3f}",
                        java_data=java_entity,
                        go_data=go_entity,
                        score_difference=score_diff
                    ))
                elif score_diff > 0.05:
                    divergences.append(Divergence(
                        type=DivergenceType.SCORE_DIFFERENCE,
                        severity="moderate",
                        description=f"Moderate score difference for {self._get_entity_name(java_entity)}: {score_diff:.3f}",
                        java_data=java_entity,
                        go_data=go_entity,
                        score_difference=score_diff
                    ))
                elif score_diff > 0.02:
                    divergences.append(Divergence(
                        type=DivergenceType.SCORE_DIFFERENCE,
                        severity="minor",
                        description=f"Minor score difference for {self._get_entity_name(java_entity)}: {score_diff:.3f}",
                        java_data=java_entity,
                        go_data=go_entity,
                        score_difference=score_diff
                    ))
        
        # Find Java-only results
        for entity_id, java_entity in java_by_id.items():
            if entity_id not in go_by_id:
                divergences.append(Divergence(
                    type=DivergenceType.JAVA_EXTRA_RESULT,
                    severity="moderate",
                    description=f"Java returned but Go didn't: {self._get_entity_name(java_entity)}",
                    java_data=java_entity
                ))
        
        # Find Go-only results
        for entity_id, go_entity in go_by_id.items():
            if entity_id not in java_by_id:
                divergences.append(Divergence(
                    type=DivergenceType.GO_EXTRA_RESULT,
                    severity="moderate",
                    description=f"Go returned but Java didn't: {self._get_entity_name(go_entity)}",
                    go_data=go_entity
                ))
        
        return divergences
    
    def get_summary(self, java_results: List[Dict], go_results: List[Dict]) -> Dict:
        """
        Get summary statistics of divergences.
        
        Args:
            java_results: Java API results
            go_results: Go API results
            
        Returns:
            Dict with summary stats
        """
        divergences = self.compare(java_results, go_results)
        
        by_type = {}
        by_severity = {}
        
        for div in divergences:
            # Count by type
            type_name = div.type.value
            by_type[type_name] = by_type.get(type_name, 0) + 1
            
            # Count by severity
            by_severity[div.severity] = by_severity.get(div.severity, 0) + 1
        
        return {
            "total_divergences": len(divergences),
            "by_type": by_type,
            "by_severity": by_severity,
            "divergences": divergences
        }
